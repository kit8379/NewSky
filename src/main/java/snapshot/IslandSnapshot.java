package snapshot;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Island;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandSnapshot {

    private final NewSky plugin;
    private final DatabaseHandler database;

    private final Map<UUID, Island> islands = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> dirty = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loading = new ConcurrentHashMap<>();

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
        loading.put(islandUuid, Boolean.TRUE);

        return CompletableFuture.supplyAsync(() -> {
            return database.getIslandSnapshot(islandUuid);
        }, plugin.getBukkitAsyncExecutor()).thenAccept(island -> {
            if (island == null) {
                islands.remove(islandUuid);
                throw new IllegalStateException("Island snapshot does not exist: " + islandUuid);
            }

            islands.put(islandUuid, island);
            dirty.remove(islandUuid);
        }).whenComplete((result, throwable) -> {
            loading.remove(islandUuid);

            if (throwable != null) {
                dirty.put(islandUuid, Boolean.TRUE);
                plugin.severe("Failed to load island snapshot: " + islandUuid, throwable);
            }
        });
    }

    public void unload(UUID islandUuid) {
        islands.remove(islandUuid);
        dirty.remove(islandUuid);
        loading.remove(islandUuid);
    }

    public void markDirty(UUID islandUuid) {
        if (islands.containsKey(islandUuid) || loading.containsKey(islandUuid)) {
            dirty.put(islandUuid, Boolean.TRUE);
        }
    }

    public CompletableFuture<Void> reload(UUID islandUuid) {
        markDirty(islandUuid);

        if (!islands.containsKey(islandUuid) && !loading.containsKey(islandUuid)) {
            dirty.remove(islandUuid);
            return CompletableFuture.completedFuture(null);
        }

        return load(islandUuid);
    }
}
