package snapshot;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.model.Island;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandLoadedSnapshot {

    private final NewSky plugin;
    private final DataCache dataCache;

    private final Map<UUID, Island> islands = new ConcurrentHashMap<>();

    public IslandLoadedSnapshot(NewSky plugin, DataCache dataCache) {
        this.plugin = plugin;
        this.dataCache = dataCache;
    }

    public Island get(UUID islandUuid) {
        return islands.get(islandUuid);
    }

    public CompletableFuture<Void> load(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return dataCache.getIslandSnapshot(islandUuid);
        }, plugin.getBukkitAsyncExecutor()).thenAccept(island -> {
            if (island == null) {
                islands.remove(islandUuid);
            } else {
                islands.put(islandUuid, island);
            }
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