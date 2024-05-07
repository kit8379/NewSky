package org.me.newsky.island;

import org.bukkit.Location;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.island.middleware.PreIslandHandler;
import org.me.newsky.util.LocationUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HomeHandler {

    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;

    public HomeHandler(CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
    }

    public CompletableFuture<Void> setHome(UUID islandUuid, UUID playerUuid, String homeName, Location location) {
        return CompletableFuture.runAsync(() -> {
            if (location.getWorld() == null || !location.getWorld().getName().equals("island-" + islandUuid)) {
                throw new LocationNotInIslandException();
            }
            String homeLocation = LocationUtils.locationToString(location);
            cacheHandler.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);
        });
    }

    public CompletableFuture<Void> delHome(UUID islandUuid, UUID playerUuid, String homeName) {
        return CompletableFuture.runAsync(() -> {
            if (cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName).isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            cacheHandler.deleteHomePoint(islandUuid, playerUuid, homeName);
        });
    }

    public CompletableFuture<Void> home(UUID islandUuid, UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName);
            if (homeLocationOpt.isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            return homeLocationOpt.get();
        }).thenCompose(homeLocation -> {
            return preIslandHandler.teleportToIsland(targetPlayerUuid, islandUuid, homeLocation);
        });
    }


    public CompletableFuture<Set<String>> getHomeNames(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getHomeNames(islandUuid, playerUuid);
        });
    }
}
