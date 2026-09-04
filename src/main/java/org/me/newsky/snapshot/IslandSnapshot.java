package org.me.newsky.snapshot;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Island;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-server cache of the islands hosted here, read on every block break, PvP hit and world change.
 * <p>
 * A loading, dirty or missing org.me.newsky.snapshot is unavailable so listeners fail closed.
 */
public class IslandSnapshot {

    private final NewSky plugin;
    private final DatabaseHandler database;

    private final Map<UUID, Island> islands = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> dirty = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loading = new ConcurrentHashMap<>();
    private final AtomicLong nextLoadGeneration = new AtomicLong();

    public IslandSnapshot(NewSky plugin, DatabaseHandler database) {
        this.plugin = plugin;
        this.database = database;
    }

    public Island get(UUID islandUuid) {
        if (dirty.containsKey(islandUuid) || loading.containsKey(islandUuid)) {
            return null;
        }

        return islands.get(islandUuid);
    }

    public CompletableFuture<Void> load(UUID islandUuid) {
        long generation = nextLoadGeneration.incrementAndGet();
        loading.put(islandUuid, generation);

        try {
            Island island = database.getIslandSnapshot(islandUuid);

            if (!Long.valueOf(generation).equals(loading.get(islandUuid))) {
                return CompletableFuture.completedFuture(null);
            }

            if (island == null) {
                islands.remove(islandUuid);
                throw new IllegalStateException("Island org.me.newsky.snapshot does not exist: " + islandUuid);
            }

            if (loading.remove(islandUuid, generation)) {
                islands.put(islandUuid, island);
                dirty.remove(islandUuid);
            }

            return CompletableFuture.completedFuture(null);
        } catch (Throwable error) {
            if (loading.remove(islandUuid, generation)) {
                dirty.put(islandUuid, Boolean.TRUE);
                plugin.severe("Failed to load island org.me.newsky.snapshot: " + islandUuid, error);
            }

            return CompletableFuture.failedFuture(error);
        }
    }

    public CompletableFuture<Void> reload(UUID islandUuid) {
        if (!islands.containsKey(islandUuid) && !loading.containsKey(islandUuid)) {
            return CompletableFuture.completedFuture(null);
        }

        dirty.put(islandUuid, Boolean.TRUE);
        return load(islandUuid);
    }

    public void unload(UUID islandUuid) {
        islands.remove(islandUuid);
        dirty.remove(islandUuid);
        loading.remove(islandUuid);
    }
}
