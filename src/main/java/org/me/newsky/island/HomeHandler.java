package org.me.newsky.island;

import org.bukkit.Location;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.island.distributor.IslandServiceDistributor;
import org.me.newsky.util.LocationUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HomeHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandServiceDistributor islandServiceDistributor;

    public HomeHandler(NewSky plugin, CacheHandler cacheHandler, IslandServiceDistributor islandServiceDistributor) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.islandServiceDistributor = islandServiceDistributor;
    }

    // Sync Getter
    public Set<String> getHomeNames(UUID playerUuid) {
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(playerUuid);
        if (islandUuidOpt.isEmpty()) {
            throw new IslandDoesNotExistException();
        }
        UUID islandUuid = islandUuidOpt.get();
        return cacheHandler.getHomeNames(islandUuid, playerUuid);
    }

    // Async Operations
    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, Location location) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (location.getWorld() == null || !location.getWorld().getName().equals("island-" + islandUuid)) {
                throw new LocationNotInIslandException();
            }
            String homeLocation = LocationUtils.locationToString(location);
            cacheHandler.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName).isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            cacheHandler.deleteHomePoint(islandUuid, playerUuid, homeName);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName);
            if (homeLocationOpt.isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            String homeLocation = homeLocationOpt.get();
            return islandServiceDistributor.teleportIsland(islandUuid, targetPlayerUuid, homeLocation);
        });
    }
}
