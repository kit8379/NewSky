package org.me.newsky.api.component;

import org.bukkit.Location;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.util.LocationUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HomeAPI {

    private final CacheHandler cacheHandler;

    public HomeAPI(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            String homeLocation = LocationUtils.locationToString(location);
            cacheHandler.updateHomePoint(playerUuid, islandUuid, homeName, homeLocation);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName).isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            cacheHandler.deleteHomePoint(playerUuid, islandUuid, homeName);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName);
            if (homeLocationOpt.isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            Location loc = LocationUtils.parseLocation(homeLocationOpt.get());
            // Teleport the player to the home location
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Set<String>> getHomeNames(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        }).thenApply(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            return cacheHandler.getHomeNames(islandUuid, playerUuid);
        });
    }
}
