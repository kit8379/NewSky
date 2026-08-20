package org.me.newsky.test;

import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the exact Lua scripts the plugin deploys (referenced from the production constants, never
 * copied) against a real Redis, plus the raw HSETNX race that decides island placement. Needs a
 * Redis at 127.0.0.1:6379 (override with args: host port); prints SKIPPED and exits cleanly when
 * none is reachable, so it can sit in the same run as the pure-JVM tests.
 * <p>
 * All keys are prefixed with a random test namespace and deleted afterwards.
 */
public final class RedisClaimScriptsTest {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        try (Jedis probe = new Jedis(host, port)) {
            probe.ping();
        } catch (Exception e) {
            System.out.println("RedisClaimScriptsTest: SKIPPED (no Redis reachable at " + host + ":" + port + ")");
            return;
        }

        String prefix = "newsky:test:" + UUID.randomUUID() + ":";
        try (JedisPool pool = new JedisPool(host, port)) {
            try {
                hsetnxRaceHasExactlyOneWinner(pool, prefix);
                claimOrConfirmSemantics(pool, prefix);
                releaseIfHeldBySemantics(pool, prefix);
                guardedOnlineRemoveSemantics(pool, prefix);
                System.out.println("RedisClaimScriptsTest: ALL PASS");
            } finally {
                try (Jedis jedis = pool.getResource()) {
                    jedis.keys(prefix + "*").forEach(jedis::del);
                }
            }
        }
    }

    // The exact two-servers-run-/is-home-at-once race: many concurrent HSETNX on one island,
    // exactly one may win.
    private static void hsetnxRaceHasExactlyOneWinner(JedisPool pool, String prefix) throws Exception {
        String key = prefix + "island:server";
        String island = UUID.randomUUID().toString();
        int contenders = 64;

        ExecutorService threads = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(contenders);

        for (int i = 0; i < contenders; i++) {
            String server = "server-" + i;
            threads.execute(() -> {
                try (Jedis jedis = pool.getResource()) {
                    start.await();
                    if (jedis.hsetnx(key, island, server) == 1L) {
                        wins.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        Check.that(done.await(30, TimeUnit.SECONDS), "all contenders finished");
        threads.shutdown();
        Check.that(wins.get() == 1, contenders + " concurrent claims produced exactly one winner (wins=" + wins.get() + ")");
    }

    private static void claimOrConfirmSemantics(JedisPool pool, String prefix) {
        String key = prefix + "claim";
        String island = UUID.randomUUID().toString();

        try (Jedis jedis = pool.getResource()) {
            Object claimedWhenAbsent = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM, List.of(key), List.of(island, "server-a"));
            Check.that(Long.valueOf(1L).equals(claimedWhenAbsent), "absent claim: server-a claims it");
            Check.that("server-a".equals(jedis.hget(key, island)), "claim value recorded as server-a");

            Object confirmedForHolder = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM, List.of(key), List.of(island, "server-a"));
            Check.that(Long.valueOf(1L).equals(confirmedForHolder), "holder re-confirms its own claim");

            Object refusedForOther = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM, List.of(key), List.of(island, "server-b"));
            Check.that(Long.valueOf(0L).equals(refusedForOther), "another server is refused while the claim is held");
            Check.that("server-a".equals(jedis.hget(key, island)), "refused attempt leaves the claim untouched");
        }
    }

    private static void releaseIfHeldBySemantics(JedisPool pool, String prefix) {
        String key = prefix + "release";
        String island = UUID.randomUUID().toString();

        try (Jedis jedis = pool.getResource()) {
            jedis.hset(key, island, "server-a");

            Object wrongHolder = jedis.eval(IslandRegistry.RELEASE_IF_HELD_BY, List.of(key), List.of(island, "server-b"));
            Check.that(Long.valueOf(0L).equals(wrongHolder), "release by a non-holder is refused");
            Check.that("server-a".equals(jedis.hget(key, island)), "non-holder release leaves the claim intact");

            Object rightHolder = jedis.eval(IslandRegistry.RELEASE_IF_HELD_BY, List.of(key), List.of(island, "server-a"));
            Check.that(Long.valueOf(1L).equals(rightHolder), "release by the holder succeeds");
            Check.that(jedis.hget(key, island) == null, "claim is gone after the holder releases");
        }
    }

    // The proxy-switch protection: a stale quit from the old server must not delete the entry
    // the new server's join just wrote.
    private static void guardedOnlineRemoveSemantics(JedisPool pool, String prefix) {
        String serversKey = prefix + "online:servers";
        String playersKey = prefix + "online:players";
        String player = UUID.randomUUID().toString();

        try (Jedis jedis = pool.getResource()) {
            jedis.hset(serversKey, player, "server-b");
            jedis.hset(playersKey, player, "Steve");

            Object staleQuit = jedis.eval(OnlinePlayerRegistry.REMOVE_IF_ON_SERVER, List.of(serversKey, playersKey), List.of(player, "server-a"));
            Check.that(Long.valueOf(0L).equals(staleQuit), "stale quit from the old server is refused");
            Check.that("server-b".equals(jedis.hget(serversKey, player)), "fresh entry survives the stale quit");
            Check.that("Steve".equals(jedis.hget(playersKey, player)), "player stays online after the stale quit");

            Object realQuit = jedis.eval(OnlinePlayerRegistry.REMOVE_IF_ON_SERVER, List.of(serversKey, playersKey), List.of(player, "server-b"));
            Check.that(Long.valueOf(1L).equals(realQuit), "quit from the owning server succeeds");
            Check.that(jedis.hget(serversKey, player) == null && jedis.hget(playersKey, player) == null, "both entries removed by the owning server's quit");
        }
    }
}
