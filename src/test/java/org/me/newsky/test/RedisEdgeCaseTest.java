package org.me.newsky.test;

import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.XAddParams;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Edge and failure-mode semantics against a real Redis: reaper behaviour around dead, live and
 * resurrected servers, heartbeat expiry actually flipping the reaper's verdict, the guarded
 * online-player reap sparing players who rejoined elsewhere, invite set-if-absent and atomic
 * consume under thread races, and the inbox length cap. Lua scripts are referenced from the
 * production constants, never copied. Needs a Redis (args: host port); prints SKIPPED without one.
 */
public final class RedisEdgeCaseTest {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;

        try (Jedis probe = new Jedis(host, port)) {
            probe.ping();
        } catch (Exception e) {
            System.out.println("RedisEdgeCaseTest: SKIPPED (no Redis reachable at " + host + ":" + port + ")");
            return;
        }

        String prefix = "newsky:test:" + UUID.randomUUID() + ":";
        try (JedisPool pool = new JedisPool(host, port)) {
            try {
                reaperSparesLiveAndRemovesDead(pool, prefix);
                reaperSparesResurrectedAndReclaimed(pool, prefix);
                heartbeatExpiryFlipsReaperVerdict(pool, prefix);
                onlineReapSparesPlayersWhoRejoinedElsewhere(pool, prefix);
                inviteSetIfAbsentRace(pool, prefix);
                inviteDoubleAcceptRace(pool, prefix);
                inboxLengthIsCapped(pool, prefix);
                System.out.println("RedisEdgeCaseTest: ALL PASS");
            } finally {
                try (Jedis jedis = pool.getResource()) {
                    jedis.keys(prefix + "*").forEach(jedis::del);
                }
            }
        }
    }

    private static void reaperSparesLiveAndRemovesDead(JedisPool pool, String prefix) {
        String claimKey = prefix + "island:server";
        String island = UUID.randomUUID().toString();

        try (Jedis jedis = pool.getResource()) {
            jedis.hset(claimKey, island, "server-live");
            jedis.set(prefix + "hb:server-live", "1");

            Object liveVerdict = jedis.eval(IslandRegistry.RELEASE_IF_DEAD, List.of(claimKey, prefix + "hb:server-live"), List.of(island, "server-live"));
            Check.that(Long.valueOf(0L).equals(liveVerdict), "reaper spares a claim whose holder is heartbeating");

            jedis.del(prefix + "hb:server-live");
            Object deadVerdict = jedis.eval(IslandRegistry.RELEASE_IF_DEAD, List.of(claimKey, prefix + "hb:server-live"), List.of(island, "server-live"));
            Check.that(Long.valueOf(1L).equals(deadVerdict), "reaper removes the claim once the heartbeat is gone");
            Check.that(jedis.hget(claimKey, island) == null, "dead server's claim is gone");
        }
    }

    // The two resurrection races the reaper must survive: (a) the dead server's claim was already
    // reaped elsewhere and re-claimed by a new host, (b) the "dead" server came back to life
    // between the reaper's listing and its delete.
    private static void reaperSparesResurrectedAndReclaimed(JedisPool pool, String prefix) {
        String claimKey = prefix + "island:server";

        try (Jedis jedis = pool.getResource()) {
            // (a) claim listed as dead-server-a's, but by eval time it belongs to fresh server-c
            String islandA = UUID.randomUUID().toString();
            jedis.hset(claimKey, islandA, "server-c");
            jedis.set(prefix + "hb:server-c", "1");
            Object reclaimed = jedis.eval(IslandRegistry.RELEASE_IF_DEAD, List.of(claimKey, prefix + "hb:server-a"), List.of(islandA, "server-a"));
            Check.that(Long.valueOf(0L).equals(reclaimed), "stale reap attempt cannot delete a claim re-taken by another server");
            Check.that("server-c".equals(jedis.hget(claimKey, islandA)), "the fresh claim survives the stale reap");

            // (b) holder unchanged but its heartbeat is back by eval time
            String islandB = UUID.randomUUID().toString();
            jedis.hset(claimKey, islandB, "server-b");
            jedis.set(prefix + "hb:server-b", "1");
            Object resurrected = jedis.eval(IslandRegistry.RELEASE_IF_DEAD, List.of(claimKey, prefix + "hb:server-b"), List.of(islandB, "server-b"));
            Check.that(Long.valueOf(0L).equals(resurrected), "a server that came back alive keeps its claim");
        }
    }

    private static void heartbeatExpiryFlipsReaperVerdict(JedisPool pool, String prefix) throws Exception {
        String claimKey = prefix + "island:server";
        String island = UUID.randomUUID().toString();
        String hbKey = prefix + "hb:server-ttl";

        try (Jedis jedis = pool.getResource()) {
            jedis.hset(claimKey, island, "server-ttl");
            jedis.setex(hbKey, 1L, "1");

            Object whileAlive = jedis.eval(IslandRegistry.RELEASE_IF_DEAD, List.of(claimKey, hbKey), List.of(island, "server-ttl"));
            Check.that(Long.valueOf(0L).equals(whileAlive), "claim survives while the heartbeat TTL is running");

            Thread.sleep(1500);

            Object afterExpiry = jedis.eval(IslandRegistry.RELEASE_IF_DEAD, List.of(claimKey, hbKey), List.of(island, "server-ttl"));
            Check.that(Long.valueOf(1L).equals(afterExpiry), "claim is reaped once the heartbeat TTL expires");
        }
    }

    private static void onlineReapSparesPlayersWhoRejoinedElsewhere(JedisPool pool, String prefix) {
        String serversKey = prefix + "online:servers";
        String playersKey = prefix + "online:players";

        try (Jedis jedis = pool.getResource()) {
            // Player stuck on crashed server-a: reaped.
            String stuck = UUID.randomUUID().toString();
            jedis.hset(serversKey, stuck, "server-a");
            jedis.hset(playersKey, stuck, "Stuck");
            Object reaped = jedis.eval(OnlinePlayerRegistry.REMOVE_IF_ON_DEAD_SERVER, List.of(serversKey, playersKey, prefix + "hb:server-a"), List.of(stuck, "server-a"));
            Check.that(Long.valueOf(1L).equals(reaped), "player stranded on a dead server is reaped");
            Check.that(jedis.hget(playersKey, stuck) == null, "stranded player no longer shows online");

            // Player listed on dead server-a but rejoined on server-b before the eval: spared.
            String rejoined = UUID.randomUUID().toString();
            jedis.hset(serversKey, rejoined, "server-b");
            jedis.hset(playersKey, rejoined, "Rejoined");
            Object spared = jedis.eval(OnlinePlayerRegistry.REMOVE_IF_ON_DEAD_SERVER, List.of(serversKey, playersKey, prefix + "hb:server-a"), List.of(rejoined, "server-a"));
            Check.that(Long.valueOf(0L).equals(spared), "player who rejoined elsewhere is spared by the reap");
            Check.that("Rejoined".equals(jedis.hget(playersKey, rejoined)), "rejoined player stays online");
        }
    }

    // Mirrors InvitationStore.addIslandInvite: SET NX EX. 32 racing inviters, exactly one stored.
    private static void inviteSetIfAbsentRace(JedisPool pool, String prefix) throws Exception {
        String inviteKey = prefix + "invitation:" + UUID.randomUUID();
        int contenders = 32;

        AtomicInteger wins = new AtomicInteger();
        runConcurrently(contenders, i -> {
            try (Jedis jedis = pool.getResource()) {
                if (jedis.set(inviteKey, "island-" + i + ":inviter-" + i, SetParams.setParams().nx().ex(60)) != null) {
                    wins.incrementAndGet();
                }
            }
        });

        Check.that(wins.get() == 1, contenders + " racing inviters stored exactly one invite (wins=" + wins.get() + ")");
        try (Jedis jedis = pool.getResource()) {
            Check.that(jedis.ttl(inviteKey) > 0, "stored invite carries its TTL");
        }
    }

    // Mirrors InvitationStore.consumeIslandInvite: GETDEL. 32 racing accepts, exactly one redeems.
    private static void inviteDoubleAcceptRace(JedisPool pool, String prefix) throws Exception {
        String inviteKey = prefix + "invitation:" + UUID.randomUUID();
        try (Jedis jedis = pool.getResource()) {
            jedis.set(inviteKey, "island:inviter");
        }

        int contenders = 32;
        AtomicInteger redeemed = new AtomicInteger();
        runConcurrently(contenders, i -> {
            try (Jedis jedis = pool.getResource()) {
                if (jedis.getDel(inviteKey) != null) {
                    redeemed.incrementAndGet();
                }
            }
        });

        Check.that(redeemed.get() == 1, contenders + " racing accepts redeemed the invite exactly once (redeemed=" + redeemed.get() + ")");
    }

    // Mirrors CrossServerMessenger.send: XADD with approximate MAXLEN. A dead inbox flooded with
    // 20k messages must stay bounded instead of growing without limit.
    private static void inboxLengthIsCapped(JedisPool pool, String prefix) {
        String inboxKey = prefix + "inbox:dead-server";
        long maxLen = 1000L;
        int flooded = 20_000;

        try (Jedis jedis = pool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            for (int i = 0; i < flooded; i++) {
                pipeline.xadd(inboxKey, XAddParams.xAddParams().id(StreamEntryID.NEW_ENTRY).maxLen(maxLen).approximateTrimming(), Map.of("message", "m" + i));
            }
            pipeline.sync();

            long length = jedis.xlen(inboxKey);
            Check.that(length >= maxLen && length < 2 * maxLen, flooded + " floods left the inbox bounded near " + maxLen + " (len=" + length + ")");
        }
    }

    private static void runConcurrently(int threads, java.util.function.IntConsumer action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            int index = i;
            pool.execute(() -> {
                try {
                    start.await();
                    action.accept(index);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        Check.that(done.await(30, TimeUnit.SECONDS), "all racing threads finished");
        pool.shutdown();
    }
}
