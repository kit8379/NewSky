package org.me.newsky.island;

import org.bukkit.Location;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.island.middleware.IslandServiceDistributor;
import org.me.newsky.util.LocationUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WarpHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandServiceDistributor islandServiceDistributor;

    public WarpHandler(NewSky plugin, CacheHandler cacheHandler, IslandServiceDistributor islandServiceDistributor) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.islandServiceDistributor = islandServiceDistributor;
    }

    // Sync Getter
    public Set<String> getWarpNames(UUID playerUuid) {
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(playerUuid);
        if (islandUuidOpt.isEmpty()) {
            throw new IslandDoesNotExistException();
        }
        UUID islandUuid = islandUuidOpt.get();
        return cacheHandler.getWarpNames(islandUuid, playerUuid);
    }

    // Async Operations
    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, Location location) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
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
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
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

    public CompletableFuture<Void> warp(UUID playerUuid, String warpName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            if (cacheHandler.isPlayerBanned(islandUuid, targetPlayerUuid)) {
                throw new PlayerBannedException();
            }

            boolean isLocked = cacheHandler.isIslandLock(islandUuid);
            boolean isMember = cacheHandler.getIslandPlayers(islandUuid).contains(targetPlayerUuid);
            if (isLocked && !isMember) {
                throw new IslandLockedException();
            }

            Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName);
            if (warpLocationOpt.isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            String warpLocation = warpLocationOpt.get();
            return islandServiceDistributor.teleportToIsland(islandUuid, targetPlayerUuid, warpLocation);
        });
    }
}
