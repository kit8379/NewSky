package org.me.newsky.test;

import org.me.newsky.model.Island;
import org.me.newsky.snapshot.IslandSnapshot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ordering and staleness rules of the per-server snapshot cache. These are the guarantees the
 * protection, PvP and access listeners depend on, so they are pinned here rather than argued
 * about: reads for one island never overlap, an older read can never overwrite a newer one (even
 * across an unload/reload cycle - the ABA case), a failed read leaves the previous snapshot in
 * place, a deleted island drops out of the cache, and a reload for an island this server does not
 * host touches no database at all.
 */
public final class IslandSnapshotTest {

    private static final UUID ISLAND = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    public static void main(String[] args) throws Exception {
        loadCachesTheSnapshot();
        readsForOneIslandDoNotOverlap();
        olderReadCannotOverwriteNewer();
        staleReadCannotResurrectAfterUnloadAndReload();
        unloadDuringReadLeavesNothingCached();
        reloadIsNoOpWhenNotHosted();
        failedReadKeepsPreviousSnapshot();
        deletedIslandDropsOutOfCache();
        concurrentChurnNeverMovesSnapshotBackwards();
        System.out.println("IslandSnapshotTest: ALL PASS");
    }

    private static Island island(int version) {
        // The member set doubles as the version marker: it is what listeners actually read.
        return new Island(ISLAND, false, false, OWNER, Set.of(new UUID(0L, version)), Set.of(), Set.of());
    }

    private static int versionOf(Island island) {
        if (island == null) {
            return -1;
        }
        return (int) island.getMembers().iterator().next().getLeastSignificantBits();
    }

    private static void loadCachesTheSnapshot() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> island(1), (m, e) -> {
            });

            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);
            Check.that(versionOf(snapshot.get(ISLAND)) == 1, "load caches the snapshot");
        } finally {
            executor.shutdownNow();
        }
    }

    // Two reads of one island must not run concurrently: overlapping reads are what makes
    // out-of-order completion (and permanent staleness) possible in the first place.
    private static void readsForOneIslandDoNotOverlap() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            AtomicInteger concurrent = new AtomicInteger();
            AtomicBoolean overlapped = new AtomicBoolean(false);
            AtomicInteger version = new AtomicInteger();

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                if (concurrent.incrementAndGet() > 1) {
                    overlapped.set(true);
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                concurrent.decrementAndGet();
                return island(version.incrementAndGet());
            }, (m, e) -> {
            });

            CompletableFuture<?>[] loads = new CompletableFuture<?>[20];
            for (int i = 0; i < loads.length; i++) {
                loads[i] = snapshot.load(ISLAND);
            }
            CompletableFuture.allOf(loads).get(20, TimeUnit.SECONDS);

            Check.that(!overlapped.get(), "20 concurrent load calls never ran two reads at once");
        } finally {
            executor.shutdownNow();
        }
    }

    // A slow read enqueued first must not land after a fast read enqueued second.
    private static void olderReadCannotOverwriteNewer() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            AtomicInteger call = new AtomicInteger();

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                int nth = call.incrementAndGet();
                if (nth == 1) {
                    try {
                        Thread.sleep(150); // the older read is the slow one
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    return island(1);
                }
                return island(2);
            }, (m, e) -> {
            });

            CompletableFuture<Void> first = snapshot.load(ISLAND);
            CompletableFuture<Void> second = snapshot.load(ISLAND);
            CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);

            Check.that(versionOf(snapshot.get(ISLAND)) == 2, "the newer read wins even when the older read is slower");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * The ABA case the unload generation exists for: a read is in flight, the island is unloaded,
     * then loaded again (re-creating the chain) and refreshed with current data. When the original
     * read finally completes, a bare "is this island hosted?" check would pass - the chain is back -
     * and its stale value would overwrite the fresh one permanently. The generation makes the read
     * recognise that it no longer speaks for this island.
     */
    private static void staleReadCannotResurrectAfterUnloadAndReload() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch firstReadStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstRead = new CountDownLatch(1);
            AtomicInteger call = new AtomicInteger();

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                if (call.incrementAndGet() == 1) {
                    firstReadStarted.countDown();
                    try {
                        Check.that(releaseFirstRead.await(10, TimeUnit.SECONDS), "stale read was released");
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    return island(1); // stale data, read before the unload
                }
                return island(2); // fresh data, read after the reload
            }, (m, e) -> {
            });

            CompletableFuture<Void> staleLoad = snapshot.load(ISLAND);
            Check.that(firstReadStarted.await(5, TimeUnit.SECONDS), "first read is in flight");

            snapshot.unload(ISLAND);
            CompletableFuture<Void> freshLoad = snapshot.load(ISLAND);
            freshLoad.get(5, TimeUnit.SECONDS);
            Check.that(versionOf(snapshot.get(ISLAND)) == 2, "reload after unload cached the fresh snapshot");

            releaseFirstRead.countDown();
            staleLoad.get(10, TimeUnit.SECONDS);

            Check.that(versionOf(snapshot.get(ISLAND)) == 2, "the stale in-flight read did not resurrect old data after unload+reload");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void unloadDuringReadLeavesNothingCached() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch readStarted = new CountDownLatch(1);
            CountDownLatch releaseRead = new CountDownLatch(1);

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                readStarted.countDown();
                try {
                    Check.that(releaseRead.await(10, TimeUnit.SECONDS), "read was released");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return island(1);
            }, (m, e) -> {
            });

            CompletableFuture<Void> load = snapshot.load(ISLAND);
            Check.that(readStarted.await(5, TimeUnit.SECONDS), "read is in flight");

            snapshot.unload(ISLAND);
            releaseRead.countDown();
            load.get(10, TimeUnit.SECONDS);

            Check.that(snapshot.get(ISLAND) == null, "a read completing after unload caches nothing");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void reloadIsNoOpWhenNotHosted() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AtomicInteger reads = new AtomicInteger();
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                reads.incrementAndGet();
                return island(1);
            }, (m, e) -> {
            });

            snapshot.reload(ISLAND).get(5, TimeUnit.SECONDS);
            Check.that(reads.get() == 0, "reload for an island this server does not host reads no database");

            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);
            snapshot.reload(ISLAND).get(5, TimeUnit.SECONDS);
            Check.that(reads.get() == 2, "reload for a hosted island does re-read");

            snapshot.unload(ISLAND);
            snapshot.reload(ISLAND).get(5, TimeUnit.SECONDS);
            Check.that(reads.get() == 2, "reload after unload reads no database again");
        } finally {
            executor.shutdownNow();
        }
    }

    // Stale must beat absent on the hot path: listeners fail closed on null, so a transient
    // database failure must not make a live island look non-existent.
    private static void failedReadKeepsPreviousSnapshot() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AtomicInteger call = new AtomicInteger();
            AtomicReference<String> logged = new AtomicReference<>();

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                if (call.incrementAndGet() == 1) {
                    return island(1);
                }
                throw new IllegalStateException("database down");
            }, (m, e) -> logged.set(m));

            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);

            boolean failed = false;
            try {
                snapshot.reload(ISLAND).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                failed = true;
            }

            Check.that(failed, "a failing reload reports the failure to its caller");
            Check.that(versionOf(snapshot.get(ISLAND)) == 1, "the previous snapshot survives a failed read");
            Check.that(logged.get() != null, "the failed read was logged");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void deletedIslandDropsOutOfCache() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AtomicBoolean deleted = new AtomicBoolean(false);
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> deleted.get() ? null : island(1), (m, e) -> {
            });

            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);
            Check.that(snapshot.get(ISLAND) != null, "island cached while its rows exist");

            deleted.set(true);
            try {
                snapshot.reload(ISLAND).get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // expected: a missing island reports failure
            }

            Check.that(snapshot.get(ISLAND) == null, "an island whose rows are gone drops out of the cache");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Property test for the anti-staleness rule under real churn: with a reader whose versions
     * only ever increase, the cached snapshot must never move backwards. A watcher samples the
     * cache throughout, so an older read landing on top of a newer one is caught even if the
     * final state happens to look correct.
     */
    private static void concurrentChurnNeverMovesSnapshotBackwards() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        ExecutorService drivers = Executors.newFixedThreadPool(9);
        try {
            AtomicInteger version = new AtomicInteger();
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(3));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return island(version.incrementAndGet());
            }, (m, e) -> {
            });

            ConcurrentLinkedQueue<String> regressions = new ConcurrentLinkedQueue<>();
            AtomicBoolean running = new AtomicBoolean(true);
            AtomicInteger unloads = new AtomicInteger();

            drivers.execute(() -> {
                int highest = -1;
                while (running.get()) {
                    Island cached = snapshot.get(ISLAND);
                    int seen = versionOf(cached);
                    if (seen >= 0) {
                        if (seen < highest) {
                            regressions.add("cache went from version " + highest + " back to " + seen);
                        } else {
                            highest = seen;
                        }
                    }
                    Thread.onSpinWait();
                }
            });

            CountDownLatch done = new CountDownLatch(8);
            for (int t = 0; t < 8; t++) {
                drivers.execute(() -> {
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    try {
                        for (int i = 0; i < 150; i++) {
                            int dice = rnd.nextInt(100);
                            if (dice < 55) {
                                snapshot.load(ISLAND);
                            } else if (dice < 85) {
                                snapshot.reload(ISLAND);
                            } else {
                                snapshot.unload(ISLAND);
                                unloads.incrementAndGet();
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }

            Check.that(done.await(60, TimeUnit.SECONDS), "churn drivers finished");
            snapshot.load(ISLAND).get(20, TimeUnit.SECONDS);
            running.set(false);

            Check.that(unloads.get() > 0, "the churn included unloads (" + unloads + ")");
            Check.that(regressions.isEmpty(), "the cached snapshot never moved backwards under churn (" + regressions + ")");
        } finally {
            drivers.shutdownNow();
            executor.shutdownNow();
        }
    }
}
