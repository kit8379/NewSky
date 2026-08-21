package org.me.newsky.snapshot;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Island;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * Per-server authoritative copy of the islands hosted here, read on every block break, PvP hit
 * and world change.
 * <p>
 * This is not a cache that chases the database: the database is read exactly once per hosting -
 * the seed, at world load - and from then on every write applies its own known delta in the same
 * operation that committed it. There is nothing to refresh, retry or reconcile, because there is
 * no moment where memory has to "catch up": a write completes only after both the database row
 * and the hosted copy reflect it.
 * <p>
 * Ordering rules, all enforced by one per-island chain:
 * <ul>
 *   <li>Deltas queue behind an in-flight seed, so a delta committed during the seed's read is
 *       applied on top of it - and every delta is idempotent, because the seed may already
 *       contain it.</li>
 *   <li>Concurrent seed requests coalesce into one read.</li>
 *   <li>An unload bumps the island's generation, so a seed left over from before the unload can
 *       never resurrect or overwrite a fresher hosting.</li>
 * </ul>
 * Listeners treat a missing island as "does not exist" and fail closed; null means "not hosted
 * here", never "busy".
 */
public class IslandSnapshot {

    /**
     * Blocking read of one island's snapshot row set. Returns null when the island has no rows.
     * Exists so {@code IslandSnapshotTest} can drive the ordering rules with a controlled reader
     * instead of a live database.
     */
    @FunctionalInterface
    public interface Reader {
        Island read(UUID islandUuid);
    }

    @FunctionalInterface
    public interface ErrorSink {
        void error(String message, Throwable error);
    }

    private final Executor executor;
    private final Reader reader;
    private final ErrorSink errorSink;

    private final Map<UUID, Island> islands = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> pendingSeeds = new ConcurrentHashMap<>();

    // Bumped on every unload. A seed caches its result only if the generation it was enqueued
    // under is still current: without this, a read left over from before an unload could pass a
    // bare "is the island hosted" check (a newer load re-created the chain) and overwrite that
    // newer load's fresher result with its stale one.
    private final Map<UUID, Long> unloadGenerations = new ConcurrentHashMap<>();

    public IslandSnapshot(NewSky plugin, DatabaseHandler database) {
        this(plugin.getBukkitAsyncExecutor(), database::getIslandSnapshot, plugin::severe);
    }

    public IslandSnapshot(Executor executor, Reader reader, ErrorSink errorSink) {
        this.executor = executor;
        this.reader = reader;
        this.errorSink = errorSink;
    }

    /**
     * The hosted state of this island, or null if this server does not host it. Null means
     * "not hosted", never "busy".
     */
    public Island get(UUID islandUuid) {
        return islands.get(islandUuid);
    }

    /**
     * Seeds the island from the database - the one and only database read of a hosting, called at
     * world load. Concurrent seed requests for the same island coalesce into a single read.
     */
    public CompletableFuture<Void> load(UUID islandUuid) {
        return pendingSeeds.computeIfAbsent(islandUuid, uuid -> {
            long generation = currentGeneration(uuid);

            CompletableFuture<Void> seed = chains.compute(uuid, (key, previous) -> {
                CompletableFuture<Void> settled = previous == null ? CompletableFuture.completedFuture(null) : previous.handle((result, error) -> null);
                return settled.thenCompose(v -> read(key, generation));
            });

            seed.whenComplete((result, error) -> pendingSeeds.remove(uuid, seed));
            return seed;
        });
    }

    /**
     * Applies a write's own delta to the hosted copy, queued behind any in-flight seed so the
     * result always lands on seeded state. A no-op when this server does not host the island -
     * there is no copy to maintain. The delta must be idempotent: it may run on top of a seed
     * that already observed the committed write.
     */
    public CompletableFuture<Void> apply(UUID islandUuid, UnaryOperator<Island> delta) {
        CompletableFuture<Void> done = new CompletableFuture<>();

        CompletableFuture<Void> chained = chains.computeIfPresent(islandUuid, (uuid, previous) -> previous.handle((result, error) -> null).thenRun(() -> {
            try {
                islands.computeIfPresent(uuid, (key, current) -> delta.apply(current));
                done.complete(null);
            } catch (Throwable t) {
                errorSink.error("Failed to apply snapshot delta for island: " + islandUuid, t);
                done.completeExceptionally(t);
            }
        }));

        if (chained == null) {
            // Not hosted here: the write's truth is in the database, and whoever seeds this
            // island later reads it from there.
            done.complete(null);
        }

        return done;
    }

    /**
     * Applies a delta only at the exact next durable database version. A duplicate/late delta is
     * ignored; a version gap triggers a full consistent read. This turns unexpected out-of-band
     * writes or a missed completion callback into bounded reconciliation instead of permanent
     * enforcement staleness.
     */
    public CompletableFuture<Void> applyVersioned(UUID islandUuid, long committedVersion, UnaryOperator<Island> delta) {
        CompletableFuture<Void> chained = chains.computeIfPresent(islandUuid, (uuid, previous) ->
                previous.handle((result, error) -> null).thenCompose(v -> {
                    Island current = islands.get(uuid);
                    if (current == null || committedVersion <= current.getStateVersion()) {
                        return CompletableFuture.completedFuture(null);
                    }

                    if (committedVersion == current.getStateVersion() + 1) {
                        try {
                            islands.computeIfPresent(uuid, (key, value) ->
                                    delta.apply(value).withStateVersion(committedVersion));
                            return CompletableFuture.completedFuture(null);
                        } catch (Throwable t) {
                            errorSink.error("Failed to apply versioned snapshot delta for island: " + islandUuid, t);
                            return CompletableFuture.failedFuture(t);
                        }
                    }

                    errorSink.error("Snapshot version gap for island " + islandUuid + ": memory="
                            + current.getStateVersion() + ", committed=" + committedVersion + "; reconciling",
                            new IllegalStateException("Snapshot delta sequence contains a gap"));
                    return read(uuid, currentGeneration(uuid));
                }));

        return chained == null ? CompletableFuture.completedFuture(null) : chained;
    }

    public void unload(UUID islandUuid) {
        unloadGenerations.merge(islandUuid, 1L, Long::sum);
        islands.remove(islandUuid);
        chains.remove(islandUuid);
        pendingSeeds.remove(islandUuid);
    }

    private long currentGeneration(UUID islandUuid) {
        return unloadGenerations.getOrDefault(islandUuid, 0L);
    }

    private CompletableFuture<Void> read(UUID islandUuid, long generation) {
        return CompletableFuture.supplyAsync(() -> reader.read(islandUuid), executor).thenAccept(island -> {
            if (island == null) {
                // The island genuinely has no row any more, so the hosted copy has to go: listeners
                // must stop enforcing rules from a snapshot with nothing behind it.
                islands.remove(islandUuid);
                throw new IllegalStateException("Island snapshot does not exist: " + islandUuid);
            }

            if (currentGeneration(islandUuid) != generation) {
                // Unloaded (and possibly reloaded) while this read was in flight; caching it now
                // would resurrect a stale snapshot on an island this read no longer speaks for.
                return;
            }

            islands.put(islandUuid, island);

            if (currentGeneration(islandUuid) != generation) {
                // An unload slipped in between the check and the put; whichever of the two
                // removals runs last wins, so the resurrected entry cannot survive.
                islands.remove(islandUuid, island);
            }
        }).exceptionallyCompose(error -> {
            errorSink.error("Failed to seed island snapshot: " + islandUuid, error);
            return CompletableFuture.failedFuture(error);
        });
    }
}
