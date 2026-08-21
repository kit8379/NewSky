package org.me.newsky.test;

import org.me.newsky.cluster.IslandRegistry;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The write-versus-load race under the write-authority protocol, modeled against a real Redis
 * with the exact production Lua.
 * <p>
 * A writer wants to write to an island nobody hosts; a teleport concurrently claims the island
 * and seeds its snapshot from the database. Under the old notify-and-retry design the seed could
 * read pre-commit state and nothing durable would ever correct it. Under write authority the race
 * is unrepresentable: the writer atomically becomes the island's temporary claim holder for the
 * duration of the commit ({@code ACQUIRE_WRITE_AUTHORITY}), so the loader either claims first
 * (and the write is routed to it, applying its delta there) or claims after the release (and its
 * seed reads the committed state).
 * <p>
 * The run ends with a negative control: the same race with no write authority - the writer just
 * checks the claim and commits - must strand stale hosts, or this test would prove nothing.
 * Needs a Redis (args: host port [iterations]); prints SKIPPED without one.
 */
public final class SnapshotPropagationTest {

    private static final String WRITER = "server-writer";
    private static final String LOADER = "server-loader";

    /** Stand-in for the island rows: a version that only ever moves forward. */
    private static final class Database {
        final AtomicInteger version = new AtomicInteger();

        void commitWrite() {
            jitter();
            version.incrementAndGet();
        }

        int read() {
            jitter();
            return version.get();
        }
    }

    /** Stand-in for the loader's IslandSnapshot. */
    private static final class Host {
        private volatile int snapshotVersion = -1;

        void seedOrApply(Database database) {
            snapshotVersion = database.read();
        }

        int snapshot() {
            return snapshotVersion;
        }
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 6379;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 3000;

        try (Jedis probe = new Jedis(host, port)) {
            probe.ping();
        } catch (Exception e) {
            System.out.println("SnapshotPropagationTest: SKIPPED (no Redis reachable at " + host + ":" + port + ")");
            return;
        }

        String prefix = "newsky:test:" + UUID.randomUUID() + ":";
        String claimKey = prefix + "island:server";

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);

        try (JedisPool pool = new JedisPool(poolConfig, host, port)) {
            try {
                int withAuthority = runRace(pool, claimKey, iterations, true);
                Check.that(withAuthority == 0, iterations + " races under write authority left no host stale (violations=" + withAuthority + ")");

                int withoutAuthority = runRace(pool, claimKey, iterations, false);
                Check.that(withoutAuthority > 0, "negative control: without write authority the same race does strand a stale host (violations=" + withoutAuthority + ")");

                System.out.println("SnapshotPropagationTest: ALL PASS (iterations=" + iterations + ", stale-with-authority=" + withAuthority + ", stale-without-authority=" + withoutAuthority + ")");
            } finally {
                try (Jedis jedis = pool.getResource()) {
                    jedis.keys(prefix + "*").forEach(jedis::del);
                }
            }
        }
    }

    private static int runRace(JedisPool pool, String claimKey, int iterations, boolean writeAuthority) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(2);
        AtomicInteger violations = new AtomicInteger();

        try {
            for (int i = 0; i < iterations; i++) {
                String island = UUID.randomUUID().toString();
                Database database = new Database();
                Host loaderHost = new Host();

                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(2);

                // The writer.
                threads.execute(() -> {
                    try {
                        start.await();

                        if (writeAuthority) {
                            String authority;
                            try (Jedis jedis = pool.getResource()) {
                                authority = String.valueOf(jedis.eval(IslandRegistry.ACQUIRE_WRITE_AUTHORITY, List.of(claimKey), List.of(island, WRITER)));
                            }

                            if ("claimed".equals(authority)) {
                                database.commitWrite();
                                try (Jedis jedis = pool.getResource()) {
                                    jedis.eval(IslandRegistry.RELEASE_IF_HELD_BY, List.of(claimKey), List.of(island, WRITER));
                                }
                            } else {
                                // OTHER: routed to the claim holder, which commits and applies
                                // its own delta to its hosted copy.
                                database.commitWrite();
                                loaderHost.seedOrApply(database);
                            }
                            return;
                        }

                        // Negative control: the old shape - read the claim, then just commit.
                        String holder;
                        try (Jedis jedis = pool.getResource()) {
                            holder = jedis.hget(claimKey, island);
                        }
                        database.commitWrite();
                        if (holder != null && !holder.equals(WRITER)) {
                            loaderHost.seedOrApply(database);
                        }
                    } catch (Exception e) {
                        violations.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });

                // The teleport: claim the island, then seed. Losing the claim to the writer's
                // temporary authority means waiting for its release - in production the load is
                // queued behind the write in the holder's per-island chain.
                threads.execute(() -> {
                    try {
                        start.await();

                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                        while (System.nanoTime() < deadline) {
                            boolean claimed;
                            try (Jedis jedis = pool.getResource()) {
                                claimed = jedis.hsetnx(claimKey, island, LOADER) == 1L;
                            }

                            if (claimed) {
                                loaderHost.seedOrApply(database);
                                return;
                            }

                            String holder;
                            try (Jedis jedis = pool.getResource()) {
                                holder = jedis.hget(claimKey, island);
                            }
                            if (holder == null || holder.equals(WRITER)) {
                                Thread.onSpinWait(); // writer's temporary claim; wait for release
                                continue;
                            }
                            return;
                        }
                        violations.incrementAndGet(); // never got to load: liveness failure
                    } catch (Exception e) {
                        violations.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });

                start.countDown();
                Check.silently(done.await(30, TimeUnit.SECONDS), "race participants finished");

                // Whoever ends up hosting the island must not serve a snapshot older than the
                // committed write.
                String finalHolder;
                try (Jedis jedis = pool.getResource()) {
                    finalHolder = jedis.hget(claimKey, island);
                }

                if (LOADER.equals(finalHolder) && loaderHost.snapshot() >= 0 && loaderHost.snapshot() < database.version.get()) {
                    violations.incrementAndGet();
                }
            }
        } finally {
            threads.shutdownNow();
        }

        return violations.get();
    }

    private static void jitter() {
        int nanos = ThreadLocalRandom.current().nextInt(20_000);
        long until = System.nanoTime() + nanos;
        while (System.nanoTime() < until) {
            Thread.onSpinWait();
        }
    }
}
