package org.me.newsky.test;

import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.cluster.ServerRegistry;
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
 * copied) against a real Redis, including the live-incarnation claim race. Needs a
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
                liveClaimRaceHasExactlyOneWinner(pool, prefix);
                claimOrConfirmSemantics(pool, prefix);
                releaseIfHeldBySemantics(pool, prefix);
                guardedOnlineRemoveSemantics(pool, prefix);
                serverIncarnationRegistrationIsAtomic(pool, prefix);
                System.out.println("RedisClaimScriptsTest: ALL PASS");
            } finally {
                try (Jedis jedis = pool.getResource()) {
                    jedis.keys(prefix + "*").forEach(jedis::del);
                }
            }
        }
    }

    // The exact two-servers-run-/is-home-at-once race: many concurrent fenced claims on one island,
    // exactly one may win.
    private static void liveClaimRaceHasExactlyOneWinner(JedisPool pool, String prefix) throws Exception {
        String key = prefix + "island:server";
        String island = UUID.randomUUID().toString();
        int contenders = 64;

        ExecutorService threads = Executors.newFixedThreadPool(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(contenders);

        try {
            // Keep one setup connection while enqueuing all contenders. The old harness borrowed
            // a setup connection after each worker was launched; with the default eight-slot pool,
            // the first eight workers could hold every connection while waiting for this latch,
            // deadlocking the main thread before it was able to count the latch down.
            try (Jedis setup = pool.getResource()) {
                for (int i = 0; i < contenders; i++) {
                    String server = "server-" + i;
                    String instance = UUID.randomUUID().toString();
                    String encoded = instance + ":" + server;
                    setup.set(prefix + "hb:" + server, instance);
                    threads.execute(() -> {
                        try (Jedis jedis = pool.getResource()) {
                            start.await();
                            Object verdict = jedis.eval(IslandRegistry.CLAIM_IF_LIVE,
                                    List.of(key, prefix + "hb:" + server), List.of(island, encoded, instance));
                            if (Long.valueOf(1L).equals(verdict)) {
                                wins.incrementAndGet();
                            }
                        } catch (Exception ignored) {
                        } finally {
                            done.countDown();
                        }
                    });
                }
            }

            start.countDown();
            Check.that(done.await(30, TimeUnit.SECONDS), "all contenders finished");
            Check.that(wins.get() == 1, contenders + " concurrent claims produced exactly one winner (wins=" + wins.get() + ")");
        } finally {
            start.countDown();
            threads.shutdownNow();
            if (!threads.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("claim-race executor did not terminate");
            }
        }
    }

    private static void claimOrConfirmSemantics(JedisPool pool, String prefix) {
        String key = prefix + "claim";
        String island = UUID.randomUUID().toString();
        IslandRegistry.HostClaim serverA = new IslandRegistry.HostClaim("server-a", UUID.randomUUID().toString());
        IslandRegistry.HostClaim serverB = new IslandRegistry.HostClaim("server-b", UUID.randomUUID().toString());

        try (Jedis jedis = pool.getResource()) {
            jedis.set(prefix + "hb:server-a", serverA.instanceId());
            jedis.set(prefix + "hb:server-b", serverB.instanceId());
            Object claimedWhenAbsent = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM,
                    List.of(key, prefix + "hb:server-a"), List.of(island, serverA.encoded(), serverA.instanceId()));
            Check.that(Long.valueOf(1L).equals(claimedWhenAbsent), "absent claim: server-a claims it");
            Check.that(serverA.encoded().equals(jedis.hget(key, island)), "claim records the exact server-a incarnation");

            Object confirmedForHolder = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM,
                    List.of(key, prefix + "hb:server-a"), List.of(island, serverA.encoded(), serverA.instanceId()));
            Check.that(Long.valueOf(1L).equals(confirmedForHolder), "holder re-confirms its own claim");

            Object refusedForOther = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM,
                    List.of(key, prefix + "hb:server-b"), List.of(island, serverB.encoded(), serverB.instanceId()));
            Check.that(Long.valueOf(0L).equals(refusedForOther), "another server is refused while the claim is held");
            Check.that(serverA.encoded().equals(jedis.hget(key, island)), "refused attempt leaves the claim untouched");

            jedis.set(prefix + "hb:server-a", UUID.randomUUID().toString());
            Object fencedOldHolder = jedis.eval(IslandRegistry.CLAIM_OR_CONFIRM,
                    List.of(key, prefix + "hb:server-a"), List.of(island, serverA.encoded(), serverA.instanceId()));
            Check.that(Long.valueOf(-1L).equals(fencedOldHolder), "old server-a JVM is fenced after its heartbeat incarnation changes");
        }
    }

    private static void releaseIfHeldBySemantics(JedisPool pool, String prefix) {
        String key = prefix + "release";
        String island = UUID.randomUUID().toString();
        IslandRegistry.HostClaim serverA = new IslandRegistry.HostClaim("server-a", UUID.randomUUID().toString());
        IslandRegistry.HostClaim serverB = new IslandRegistry.HostClaim("server-b", UUID.randomUUID().toString());

        try (Jedis jedis = pool.getResource()) {
            jedis.hset(key, island, serverA.encoded());

            Object wrongHolder = jedis.eval(IslandRegistry.RELEASE_IF_HELD_BY, List.of(key), List.of(island, serverB.encoded()));
            Check.that(Long.valueOf(0L).equals(wrongHolder), "release by a non-holder is refused");
            Check.that(serverA.encoded().equals(jedis.hget(key, island)), "non-holder release leaves the claim intact");

            Object rightHolder = jedis.eval(IslandRegistry.RELEASE_IF_HELD_BY, List.of(key), List.of(island, serverA.encoded()));
            Check.that(Long.valueOf(1L).equals(rightHolder), "release by the holder succeeds");
            Check.that(jedis.hget(key, island) == null, "claim is gone after the holder releases");
        }
    }

    // The proxy-switch protection: a stale quit from the old server must not delete the entry
    // the new server's join just wrote.
    private static void guardedOnlineRemoveSemantics(JedisPool pool, String prefix) {
        String serversKey = prefix + "online:servers";
        String playersKey = prefix + "online:players";
        String cleanupKey = prefix + "coop-cleanup";
        String cleanupLeases = prefix + "coop-cleanup-leases";
        String player = UUID.randomUUID().toString();
        IslandRegistry.HostClaim serverA = new IslandRegistry.HostClaim("server-a", UUID.randomUUID().toString());
        IslandRegistry.HostClaim serverB = new IslandRegistry.HostClaim("server-b", UUID.randomUUID().toString());

        try (Jedis jedis = pool.getResource()) {
            jedis.hset(serversKey, player, serverB.encoded());
            jedis.hset(playersKey, player, "Steve");

            Object staleQuit = jedis.eval(OnlinePlayerRegistry.REMOVE_IF_ON_SERVER,
                    List.of(serversKey, playersKey, cleanupKey, cleanupLeases), List.of(player, serverA.encoded(), "1"));
            Check.that(Long.valueOf(0L).equals(staleQuit), "stale quit from the old server is refused");
            Check.that(serverB.encoded().equals(jedis.hget(serversKey, player)), "fresh entry survives the stale quit");
            Check.that("Steve".equals(jedis.hget(playersKey, player)), "player stays online after the stale quit");

            Object realQuit = jedis.eval(OnlinePlayerRegistry.REMOVE_IF_ON_SERVER,
                    List.of(serversKey, playersKey, cleanupKey, cleanupLeases), List.of(player, serverB.encoded(), "1"));
            Check.that(Long.valueOf(1L).equals(realQuit), "quit from the owning server succeeds");
            Check.that(jedis.hget(serversKey, player) == null && jedis.hget(playersKey, player) == null, "both entries removed by the owning server's quit");
            Check.that(jedis.zscore(cleanupKey, player) != null, "owning quit durably queues coop cleanup");
        }
    }

    private static void serverIncarnationRegistrationIsAtomic(JedisPool pool, String prefix) {
        String heartbeat = prefix + "heartbeat:server-a";
        String gameHeartbeat = prefix + "game-heartbeat:server-a";
        String inbox = prefix + "inbox:server-a";
        String mspt = prefix + "mspt";
        String instanceA = UUID.randomUUID().toString();
        String instanceB = UUID.randomUUID().toString();

        try (Jedis jedis = pool.getResource()) {
            jedis.xadd(inbox, redis.clients.jedis.params.XAddParams.xAddParams(), java.util.Map.of("message", "stale"));
            Object first = jedis.eval(ServerRegistry.RENEW_INSTANCE, List.of(heartbeat, gameHeartbeat, inbox),
                    List.of(instanceA, "30", "0"));
            Check.that(Long.valueOf(1L).equals(first) && jedis.xlen(inbox) == 0L,
                    "first heartbeat atomically clears the previous boot's inbox");

            jedis.xadd(inbox, redis.clients.jedis.params.XAddParams.xAddParams(), java.util.Map.of("message", "current"));
            Object renewal = jedis.eval(ServerRegistry.RENEW_INSTANCE, List.of(heartbeat, gameHeartbeat, inbox),
                    List.of(instanceA, "30", "0"));
            Check.that(Long.valueOf(1L).equals(renewal) && jedis.xlen(inbox) == 1L,
                    "renewing the same incarnation never clears current work");

            Object duplicate = jedis.eval(ServerRegistry.RENEW_INSTANCE, List.of(heartbeat, gameHeartbeat, inbox),
                    List.of(instanceB, "30", "0"));
            Check.that(Long.valueOf(0L).equals(duplicate) && instanceA.equals(jedis.get(heartbeat)),
                    "a second live JVM cannot steal the same proxy server name");

            jedis.hset(mspt, "server-a", "10.0");
            Object staleStop = jedis.eval(ServerRegistry.REMOVE_INSTANCE_IF_CURRENT,
                    List.of(heartbeat, gameHeartbeat, mspt), List.of(instanceB, "server-a"));
            Check.that(Long.valueOf(0L).equals(staleStop) && instanceA.equals(jedis.get(heartbeat)),
                    "late shutdown from another incarnation cannot erase the live heartbeat");
        }
    }
}
