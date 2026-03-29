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

    public IslandSnapshot(NewSky plugin, DatabaseHandler database) {
        this.plugin = plugin;
        this.database = database;
    }

    public Island get(UUID islandUuid) {
        return islands.get(islandUuid);
    }

    public CompletableFuture<Void> load(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return database.getIslandSnapshot(islandUuid);
        }, plugin.getBukkitAsyncExecutor()).thenAccept(island -> {
            islands.put(islandUuid, island);
        });
    }

    public void unload(UUID islandUuid) {
        islands.remove(islandUuid);
    }

    public void reload(UUID islandUuid) {
        if (islands.containsKey(islandUuid)) {
            load(islandUuid);
        }
    }
}