package snapshot;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Island;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-server cache of the islands hosted here, read on every block break, PvP hit and world change.
 * <p>
 * Reads are never gated on a refresh being in progress: listeners treat a missing snapshot as
 * "island does not exist" and fail closed, so blanking the cache during a reload would bounce the
 * players standing on the island and deny the owner their own blocks for the duration. A snapshot
 * that is one write out of date is always the better answer than no snapshot at all.
 */
public class IslandSnapshot {

    private final NewSky plugin;
    private final DatabaseHandler database;

    private final Map<UUID, Island> islands = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> loadChains = new ConcurrentHashMap<>();

    public IslandSnapshot(NewSky plugin, DatabaseHandler database) {
        this.plugin = plugin;
        this.database = database;
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
        return loadChains.compute(islandUuid, (uuid, previous) -> {
            CompletableFuture<Void> settled = previous == null ? CompletableFuture.completedFuture(null) : previous.handle((result, error) -> null);
            return settled.thenCompose(v -> read(uuid));
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
        islands.remove(islandUuid);
        loadChains.remove(islandUuid);
    }

    private CompletableFuture<Void> read(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandSnapshot(islandUuid), plugin.getBukkitAsyncExecutor()).thenAccept(island -> {
            if (island == null) {
                // The island genuinely has no row any more, so the cached copy has to go: listeners
                // must stop enforcing rules from a snapshot with nothing behind it.
                islands.remove(islandUuid);
                throw new IllegalStateException("Island snapshot does not exist: " + islandUuid);
            }

            if (!loadChains.containsKey(islandUuid)) {
                // Unloaded while this read was in flight; caching it now would resurrect an island
                // this server no longer hosts.
                return;
            }

            islands.put(islandUuid, island);
        }).exceptionallyCompose(error -> {
            // A failed read deliberately leaves the previous snapshot in place. It is at most one
            // write out of date, while dropping it would make the island look non-existent to every
            // listener. The next write reloads it.
            plugin.severe("Failed to load island snapshot: " + islandUuid, error);
            return CompletableFuture.failedFuture(error);
        });
    }
}
