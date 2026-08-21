package org.me.newsky.test;

import org.me.newsky.model.Island;
import org.me.newsky.snapshot.IslandSnapshot;

import java.util.HashSet;
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
 * Ordering and consistency rules of the per-server island snapshot under the delta model. These
 * are the guarantees the protection, PvP and access listeners depend on, so they are pinned here:
 * the seed is read once and concurrent seed requests coalesce, deltas queue behind an in-flight
 * seed and land in application order, a delta for an island this server does not host touches
 * nothing, a stale seed can never resurrect state across an unload (the ABA case), a failed seed
 * keeps the previous snapshot, and a long random delta run leaves memory exactly equal to a
 * reference model - the property the whole no-re-read design rests on.
 */
public final class IslandSnapshotTest {

    private static final UUID ISLAND = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    public static void main(String[] args) throws Exception {
        loadCachesTheSnapshot();
        concurrentSeedsCoalesceIntoOneRead();
        sequentialLoadsDoReRead();
        staleSeedCannotResurrectAfterUnloadAndReload();
        unloadDuringSeedLeavesNothingCached();
        applyIsNoOpWhenNotHosted();
        applyQueuesBehindInFlightSeed();
        deltasApplyInOrder();
        versionedDeltasIgnoreDuplicatesAndReconcileGaps();
        failedSeedKeepsPreviousSnapshot();
        deletedIslandDropsOutOfCache();
        deltaRunMatchesReferenceModel();
        concurrentChurnNeverMovesSnapshotBackwards();
        System.out.println("IslandSnapshotTest: ALL PASS");
    }

    private static Island island(int version) {
        // The member set doubles as the version marker: deltas in these tests only touch bans,
        // so the marker survives them.
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
            Check.that(versionOf(snapshot.get(ISLAND)) == 1, "load caches the seed");
        } finally {
            executor.shutdownNow();
        }
    }

    // The user-facing point of coalescing: twenty players teleporting to one island while its
    // seed is still reading must produce one database read, not a queue of twenty.
    private static void concurrentSeedsCoalesceIntoOneRead() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            AtomicInteger reads = new AtomicInteger();
            CountDownLatch readStarted = new CountDownLatch(1);
            CountDownLatch releaseRead = new CountDownLatch(1);

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                reads.incrementAndGet();
                readStarted.countDown();
                try {
                    Check.silently(releaseRead.await(10, TimeUnit.SECONDS), "seed released");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return island(1);
            }, (m, e) -> {
            });

            CompletableFuture<Void> first = snapshot.load(ISLAND);
            Check.that(readStarted.await(5, TimeUnit.SECONDS), "seed is in flight");

            CompletableFuture<?>[] loads = new CompletableFuture<?>[20];
            for (int i = 0; i < loads.length; i++) {
                loads[i] = snapshot.load(ISLAND);
            }

            releaseRead.countDown();
            first.get(10, TimeUnit.SECONDS);
            CompletableFuture.allOf(loads).get(10, TimeUnit.SECONDS);

            Check.that(reads.get() == 1, "twenty loads during one in-flight seed coalesced into a single read (reads=" + reads + ")");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void sequentialLoadsDoReRead() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            AtomicInteger version = new AtomicInteger();
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> island(version.incrementAndGet()), (m, e) -> {
            });

            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);
            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);

            Check.that(versionOf(snapshot.get(ISLAND)) == 2, "a load after the previous seed settled reads again");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * The ABA case the unload generation exists for: a seed is in flight, the island is unloaded,
     * then loaded again and seeded with current data. When the original read finally completes, a
     * bare "is this island hosted?" check would pass and its stale value would overwrite the fresh
     * one permanently. The generation makes the read recognise that it no longer speaks for this
     * island.
     */
    private static void staleSeedCannotResurrectAfterUnloadAndReload() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch firstReadStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstRead = new CountDownLatch(1);
            AtomicInteger call = new AtomicInteger();

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                if (call.incrementAndGet() == 1) {
                    firstReadStarted.countDown();
                    try {
                        Check.silently(releaseFirstRead.await(10, TimeUnit.SECONDS), "stale seed released");
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    return island(1); // stale data, read before the unload
                }
                return island(2); // fresh data, read after the reload
            }, (m, e) -> {
            });

            CompletableFuture<Void> staleLoad = snapshot.load(ISLAND);
            Check.that(firstReadStarted.await(5, TimeUnit.SECONDS), "first seed is in flight");

            snapshot.unload(ISLAND);
            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);
            Check.that(versionOf(snapshot.get(ISLAND)) == 2, "reload after unload seeded fresh data");

            releaseFirstRead.countDown();
            staleLoad.get(10, TimeUnit.SECONDS);

            Check.that(versionOf(snapshot.get(ISLAND)) == 2, "the stale in-flight seed did not resurrect old data after unload+reload");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void unloadDuringSeedLeavesNothingCached() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch readStarted = new CountDownLatch(1);
            CountDownLatch releaseRead = new CountDownLatch(1);

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                readStarted.countDown();
                try {
                    Check.silently(releaseRead.await(10, TimeUnit.SECONDS), "seed released");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return island(1);
            }, (m, e) -> {
            });

            CompletableFuture<Void> load = snapshot.load(ISLAND);
            Check.that(readStarted.await(5, TimeUnit.SECONDS), "seed is in flight");

            snapshot.unload(ISLAND);
            releaseRead.countDown();
            load.get(10, TimeUnit.SECONDS);

            Check.that(snapshot.get(ISLAND) == null, "a seed completing after unload caches nothing");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void applyIsNoOpWhenNotHosted() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> island(1), (m, e) -> {
            });

            UUID banned = UUID.randomUUID();
            snapshot.apply(ISLAND, island -> island.withBanAdded(banned)).get(5, TimeUnit.SECONDS);

            Check.that(snapshot.get(ISLAND) == null, "a delta for an island this server does not host touches nothing");
        } finally {
            executor.shutdownNow();
        }
    }

    // A delta committed while the seed is still reading must land on top of the seeded state,
    // never be lost under it.
    private static void applyQueuesBehindInFlightSeed() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch readStarted = new CountDownLatch(1);
            CountDownLatch releaseRead = new CountDownLatch(1);

            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                readStarted.countDown();
                try {
                    Check.silently(releaseRead.await(10, TimeUnit.SECONDS), "seed released");
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                return island(1);
            }, (m, e) -> {
            });

            CompletableFuture<Void> load = snapshot.load(ISLAND);
            Check.that(readStarted.await(5, TimeUnit.SECONDS), "seed is in flight");

            UUID banned = UUID.randomUUID();
            CompletableFuture<Void> apply = snapshot.apply(ISLAND, island -> island.withBanAdded(banned));
            Check.that(!apply.isDone(), "the delta waits for the in-flight seed");

            releaseRead.countDown();
            load.get(10, TimeUnit.SECONDS);
            apply.get(10, TimeUnit.SECONDS);

            Check.that(versionOf(snapshot.get(ISLAND)) == 1, "the seed landed");
            Check.that(snapshot.get(ISLAND).getBans().contains(banned), "the delta landed on top of the seed, not under it");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void deltasApplyInOrder() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> island(1), (m, e) -> {
            });
            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);

            UUID target = UUID.randomUUID();
            for (int i = 0; i < 200; i++) {
                snapshot.apply(ISLAND, island -> island.withBanAdded(target));
                snapshot.apply(ISLAND, island -> island.withBanRemoved(target));
            }
            snapshot.apply(ISLAND, island -> island.withBanAdded(target)).get(10, TimeUnit.SECONDS);

            Check.that(snapshot.get(ISLAND).getBans().contains(target), "401 opposing deltas applied strictly in submission order (final add wins)");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void versionedDeltasIgnoreDuplicatesAndReconcileGaps() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            UUID firstBan = UUID.randomUUID();
            UUID reconciledBan = UUID.randomUUID();
            AtomicInteger reads = new AtomicInteger();
            AtomicReference<Island> database = new AtomicReference<>(
                    new Island(ISLAND, false, false, OWNER, Set.of(), Set.of(), Set.of(), 1L));
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> {
                reads.incrementAndGet();
                return database.get();
            }, (m, e) -> {
            });

            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);
            snapshot.applyVersioned(ISLAND, 2L, island -> island.withBanAdded(firstBan)).get(5, TimeUnit.SECONDS);
            Check.that(snapshot.get(ISLAND).getStateVersion() == 2L && snapshot.get(ISLAND).getBans().contains(firstBan),
                    "the exact next durable version applies its delta");

            snapshot.applyVersioned(ISLAND, 2L, island -> island.withBanRemoved(firstBan)).get(5, TimeUnit.SECONDS);
            Check.that(snapshot.get(ISLAND).getBans().contains(firstBan), "a duplicate version is ignored instead of replayed");

            database.set(new Island(ISLAND, true, false, OWNER, Set.of(), Set.of(), Set.of(reconciledBan), 4L));
            snapshot.applyVersioned(ISLAND, 4L, island -> island.withLock(false)).get(5, TimeUnit.SECONDS);
            Check.that(snapshot.get(ISLAND).getStateVersion() == 4L && snapshot.get(ISLAND).isLock()
                            && snapshot.get(ISLAND).getBans().contains(reconciledBan),
                    "a version gap reconciles from one consistent database snapshot");
            Check.that(reads.get() == 2, "only the seed and version-gap reconciliation read the database (reads=" + reads + ")");
        } finally {
            executor.shutdownNow();
        }
    }

    // Stale must beat absent on the hot path: listeners fail closed on null, so a transient
    // database failure must not make a live island look non-existent.
    private static void failedSeedKeepsPreviousSnapshot() throws Exception {
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
                snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                failed = true;
            }

            Check.that(failed, "a failing seed reports the failure to its caller");
            Check.that(versionOf(snapshot.get(ISLAND)) == 1, "the previous snapshot survives a failed seed");
            Check.that(logged.get() != null, "the failed seed was logged");
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
                snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // expected: a missing island reports failure
            }

            Check.that(snapshot.get(ISLAND) == null, "an island whose rows are gone drops out of the cache");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * The property the delta design rests on: after any sequence of deltas, memory equals what a
     * reference model computes from the same sequence. A wrong with-method or a lost/duplicated
     * application shows up as a field mismatch here.
     */
    private static void deltaRunMatchesReferenceModel() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Island seed = new Island(ISLAND, false, false, OWNER, Set.of(new UUID(0L, 1)), Set.of(), Set.of());
            IslandSnapshot snapshot = new IslandSnapshot(executor, uuid -> seed, (m, e) -> {
            });
            snapshot.load(ISLAND).get(5, TimeUnit.SECONDS);

            Island reference = seed;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            UUID[] players = new UUID[6];
            for (int i = 0; i < players.length; i++) {
                players[i] = UUID.randomUUID();
            }

            CompletableFuture<Void> last = CompletableFuture.completedFuture(null);
            for (int i = 0; i < 2000; i++) {
                UUID player = players[rnd.nextInt(players.length)];
                int op = rnd.nextInt(10);

                java.util.function.UnaryOperator<Island> delta = switch (op) {
                    case 0 -> island -> island.withBanAdded(player);
                    case 1 -> island -> island.withBanRemoved(player);
                    case 2 -> island -> island.withCoopAdded(player);
                    case 3 -> island -> island.withCoopRemoved(player);
                    case 4 -> island -> island.withMemberAdded(player);
                    case 5 -> island -> island.withMemberRemoved(player);
                    case 6 -> island -> island.withOwner(player);
                    case 7 -> island -> island.withLock(!island.isLock());
                    case 8 -> island -> island.withPvp(!island.isPvp());
                    default -> island -> island.withMemberAdded(player).withBanAdded(player).withMemberRemoved(player);
                };

                reference = delta.apply(reference);
                last = snapshot.apply(ISLAND, delta);
            }
            last.get(20, TimeUnit.SECONDS);

            Island result = snapshot.get(ISLAND);
            Check.that(result.isLock() == reference.isLock() && result.isPvp() == reference.isPvp(), "flags match the reference model after 2000 random deltas");
            Check.that(java.util.Objects.equals(result.getOwner(), reference.getOwner()), "owner matches the reference model");
            Check.that(new HashSet<>(result.getMembers()).equals(new HashSet<>(reference.getMembers())), "members match the reference model");
            Check.that(new HashSet<>(result.getCoops()).equals(new HashSet<>(reference.getCoops())), "coops match the reference model");
            Check.that(new HashSet<>(result.getBans()).equals(new HashSet<>(reference.getBans())), "bans match the reference model");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Property test for the anti-staleness rule under real churn: with a reader whose versions
     * only ever increase, the cached snapshot must never move backwards, no matter how loads,
     * deltas and unloads interleave. A watcher samples the cache throughout, so an older seed
     * landing on top of a newer one is caught even if the final state happens to look correct.
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
            UUID banned = UUID.randomUUID();

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
                            if (dice < 50) {
                                snapshot.load(ISLAND);
                            } else if (dice < 85) {
                                snapshot.apply(ISLAND, island -> rnd.nextBoolean() ? island.withBanAdded(banned) : island.withBanRemoved(banned));
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
