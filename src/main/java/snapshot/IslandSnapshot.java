package snapshot;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Island;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Per-server cache of the islands hosted here, read on every block break, PvP hit and world change.
 * <p>
 * Reads are never gated on a refresh being in progress: listeners treat a missing snapshot as
 * "island does not exist" and fail closed, so blanking the cache during a reload would bounce the
 * players standing on the island and deny the owner their own blocks for the duration. A snapshot
 * that is one write out of date is always the better answer than no snapshot at all.
 */
public class IslandSnapshot {

    /**
     * Blocking read of one island's snapshot row set. Returns null when the island has no rows.
     * Exists so {@code IslandSnapshotTest} can drive the ordering rules below with a controlled
     * reader instead of a live database.
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
    private final Map<UUID, CompletableFuture<Void>> loadChains = new ConcurrentHashMap<>();

    // Bumped on every unload. A read caches its result only if the generation it was enqueued
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
     * The last snapshot known good for this island, or null if this server has none at all. Null
     * means "unknown", never "busy".
     */
    public Island get(UUID islandUuid) {
        return islands.get(islandUuid);
    }

    /**
     * Reads the island from the database into the cache. Loads for the same island run in sequence:
     * two reloads triggered by two writes could otherwise finish out of order and leave the older
     * read cached, which would turn a momentary staleness into a permanent one.
     */
    public CompletableFuture<Void> load(UUID islandUuid) {
        long generation = currentGeneration(islandUuid);

        return loadChains.compute(islandUuid, (uuid, previous) -> {
            CompletableFuture<Void> settled = previous == null ? CompletableFuture.completedFuture(null) : previous.handle((result, error) -> null);
            return settled.thenCompose(v -> read(uuid, generation));
        });
    }

    /**
     * Refreshes the island after a write, but only if this server hosts it. A write to an island
     * loaded elsewhere is a no-op here rather than a pointless database read.
     */
    public CompletableFuture<Void> reload(UUID islandUuid) {
        if (!loadChains.containsKey(islandUuid)) {
            return CompletableFuture.completedFuture(null);
        }

        return load(islandUuid);
    }

    public void unload(UUID islandUuid) {
        unloadGenerations.merge(islandUuid, 1L, Long::sum);
        islands.remove(islandUuid);
        loadChains.remove(islandUuid);
    }

    private long currentGeneration(UUID islandUuid) {
        return unloadGenerations.getOrDefault(islandUuid, 0L);
    }

    private CompletableFuture<Void> read(UUID islandUuid, long generation) {
        return CompletableFuture.supplyAsync(() -> reader.read(islandUuid), executor).thenAccept(island -> {
            if (island == null) {
                // The island genuinely has no row any more, so the cached copy has to go: listeners
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
            // A failed read deliberately leaves the previous snapshot in place. It is at most one
            // write out of date, while dropping it would make the island look non-existent to every
            // listener. The next write reloads it.
            errorSink.error("Failed to load island snapshot: " + islandUuid, error);
            return CompletableFuture.failedFuture(error);
        });
    }
}
