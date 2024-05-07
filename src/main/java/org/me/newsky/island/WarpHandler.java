package org.me.newsky.island;

import org.bukkit.Location;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.exceptions.WarpDoesNotExistException;
import org.me.newsky.island.middleware.PreIslandHandler;
import org.me.newsky.util.LocationUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WarpHandler {

    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;

    public WarpHandler(CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
    }

    public CompletableFuture<Void> setWarp(UUID islandUuid, UUID playerUuid, String warpName, Location location) {
        return CompletableFuture.runAsync(() -> {
            if (location.getWorld() == null || !location.getWorld().getName().equals("island-" + islandUuid)) {
                throw new LocationNotInIslandException();
            }
            String warpLocation = LocationUtils.locationToString(location);
            cacheHandler.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);
        });
    }

    public CompletableFuture<Void> delWarp(UUID islandUuid, UUID playerUuid, String warpName) {
        return CompletableFuture.runAsync(() -> {
            if (cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName).isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            cacheHandler.deleteWarpPoint(islandUuid, playerUuid, warpName);
        });
    }

    public CompletableFuture<Void> warp(UUID islandUuid, UUID playerUuid, String warpName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName);
            if (warpLocationOpt.isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            return warpLocationOpt.get();
        }).thenCompose(warpLocation -> {
            return preIslandHandler.teleportToIsland(targetPlayerUuid, islandUuid, warpLocation);
        });
    }

    public CompletableFuture<Set<String>> getWarpNames(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getWarpNames(islandUuid, playerUuid);
        });
    }
}
