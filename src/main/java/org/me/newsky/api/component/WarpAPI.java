package org.me.newsky.api.component;

import org.bukkit.Location;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.exceptions.WarpDoesNotExistException;
import org.me.newsky.island.PreIslandHandler;
import org.me.newsky.util.LocationUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WarpAPI {

    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;

    public WarpAPI(CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
    }

    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (location.getWorld() == null || !location.getWorld().getName().equals("island-" + islandUuid)) {
                throw new LocationNotInIslandException();
            }
            String warpLocation = LocationUtils.locationToString(location);
            cacheHandler.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName).isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            cacheHandler.deleteWarpPoint(islandUuid, playerUuid, warpName);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> warp(UUID playerUuid, String warpName) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName);
            if (warpLocationOpt.isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            String warpLocation = warpLocationOpt.get();
            // Teleport the player to the warp location
            preIslandHandler.teleportIsland(islandUuid, playerUuid, warpLocation);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Set<String>> getWarpNames(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenApply(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            return cacheHandler.getWarpNames(islandUuid, playerUuid);
        });
    }
}
