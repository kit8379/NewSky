package org.me.newsky.test;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The routing race around locally executed writes, modeled against a real Redis claim registry.
 * <p>
 * A writer decides "nobody hosts this island, run locally" by reading the claim, then commits to
 * the database. Concurrently a teleport claims the island and reads its snapshot. If the claim and
 * the snapshot read both slip in before the commit, nothing would ever tell the new host about the
 * write and its snapshot would stay stale indefinitely - the host enforces bans and locks from it,
 * so this is a correctness bug, not a display lag. The fix is the post-commit re-check in
 * {@code IslandDistributor.propagateSnapshotAfterLocalWrite}.
 * <p>
 * Both roles are modeled exactly as the production code orders them, including the host's
 * serialized snapshot loads (the {@code IslandSnapshot} load chain), which is what makes a refresh
 * and an in-flight initial load converge instead of clobbering each other.
 * <p>
 * The run ends with a negative control: the same race with the post-commit re-check disabled must
 * produce violations. A test that passes either way would prove nothing.
 * Needs a Redis (args: host port [iterations]); prints SKIPPED without one.
 */
public final class SnapshotPropagationTest {

    private static final String WRITER = "server-writer";
    private static final String LOADER = "server-loader";

    /** Stand-in for the island rows: a version that only ever moves forward. */
    private static final class Database {
        final AtomicInteger version = new AtomicInteger();

        int commitWrite() {
            jitter();
            return version.incrementAndGet();
        }

        int read() {
            jitter();
            return version.get();
        }
    }

    /** Stand-in for one server's IslandSnapshot: loads for an island are serialized. */
    private static final class Host {
        private final Object loadChain = new Object();
        private volatile int snapshotVersion = -1;

        void load(Database database) {
            synchronized (loadChain) {
                snapshotVersion = database.read();
            }
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
                int withFix = runRace(pool, claimKey, iterations, true);
                Check.that(withFix == 0, iterations + " races with the post-commit re-check left no host stale (violations=" + withFix + ")");

                int withoutFix = runRace(pool, claimKey, iterations, false);
                Check.that(withoutFix > 0, "negative control: without the re-check the same race does strand a stale host (violations=" + withoutFix + ")");

                System.out.println("SnapshotPropagationTest: ALL PASS (iterations=" + iterations + ", stale-with-fix=" + withFix + ", stale-without-fix=" + withoutFix + ")");
            } finally {
                try (Jedis jedis = pool.getResource()) {
                    jedis.keys(prefix + "*").forEach(jedis::del);
                }
            }
        }
    }

    private static int runRace(JedisPool pool, String claimKey, int iterations, boolean recheckAfterCommit) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(2);
        AtomicInteger violations = new AtomicInteger();

        try {
            for (int i = 0; i < iterations; i++) {
                String island = UUID.randomUUID().toString();
                Database database = new Database();
                Host loaderHost = new Host();

                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(2);

                // The writer: pre-check routing, commit, then (with the fix) re-check and refresh.
                threads.execute(() -> {
                    try {
                        start.await();

                        String holderBeforeWrite;
                        try (Jedis jedis = pool.getResource()) {
                            holderBeforeWrite = jedis.hget(claimKey, island);
                        }

                        if (holderBeforeWrite != null && !holderBeforeWrite.equals(WRITER)) {
                            // Routed to the host, which writes and reloads its own snapshot.
                            database.commitWrite();
                            loaderHost.load(database);
                            return;
                        }

                        database.commitWrite();

                        if (recheckAfterCommit) {
                            String holderAfterWrite;
                            try (Jedis jedis = pool.getResource()) {
                                holderAfterWrite = jedis.hget(claimKey, island);
                            }
                            if (holderAfterWrite != null && !holderAfterWrite.equals(WRITER)) {
                                loaderHost.load(database); // the refresh message
                            }
                        }
                    } catch (Exception e) {
                        violations.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });

                // The teleport: claim the island, then read its snapshot.
                threads.execute(() -> {
                    try {
                        start.await();

                        boolean claimed;
                        try (Jedis jedis = pool.getResource()) {
                            claimed = jedis.hsetnx(claimKey, island, LOADER) == 1L;
                        }

                        if (claimed) {
                            loaderHost.load(database);
                        }
                    } catch (Exception e) {
                        violations.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });

                start.countDown();
                Check.silently(done.await(30, TimeUnit.SECONDS), "race participants finished");

                // A host that ended up hosting the island must not be serving a snapshot older
                // than the committed write.
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
