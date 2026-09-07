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
 * A missing or failed (dirty) snapshot is unavailable so listeners fail closed. A reload in
 * flight keeps serving the previous snapshot: it is at most one mutation behind for one DB
 * round trip, and blanking it would let the access listener lobby-bounce legitimate members.
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
        if (dirty.containsKey(islandUuid)) {
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

            // Guard check and publish must be one atomic step: compute holds the key's
            // lock, so a newer load (or unload) is forced to order strictly before or
            // after the whole publish - a stalled older load can never overwrite it.
            loading.compute(islandUuid, (key, current) -> {
                if (!Long.valueOf(generation).equals(current)) {
                    return current;
                }

                islands.put(key, island);
                dirty.remove(key);
                return null;
            });

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

        return load(islandUuid);
    }

    public void unload(UUID islandUuid) {
        // Invalidate any in-flight load's guard first: after this remove, a mid-flight
        // publish either already happened (cleaned by the removes below) or loses its
        // guard and publishes nothing - no entry can be resurrected for an unloaded island.
        loading.remove(islandUuid);
        islands.remove(islandUuid);
        dirty.remove(islandUuid);
    }
}
