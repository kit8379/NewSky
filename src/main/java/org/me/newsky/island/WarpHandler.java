package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.data.DataCache;
import org.me.newsky.exceptions.*;
import org.me.newsky.network.distributor.IslandDistributor;
import org.me.newsky.util.IslandUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WarpHandler {

    private final NewSky plugin;
    private final DataCache dataCache;
    private final IslandDistributor islandDistributor;

    public WarpHandler(NewSky plugin, DataCache dataCache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.dataCache = dataCache;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, String worldName, double x, double y, double z, float yaw, float pitch) {

        return CompletableFuture.supplyAsync(() -> dataCache.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            UUID islandUuid = islandUuidOpt.get();

            if (!worldName.equals(IslandUtils.UUIDToName(islandUuid))) {
                throw new LocationNotInIslandException();
            }

            String normalizedWarpName = warpName.toLowerCase(java.util.Locale.ROOT);

            if (normalizedWarpName.isEmpty() || normalizedWarpName.length() > 32) {
                throw new WarpNameNotLegalException();
            }

            for (int i = 0; i < normalizedWarpName.length(); i++) {
                char c = normalizedWarpName.charAt(i);
                if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-') {
                    throw new WarpNameNotLegalException();
                }
            }

            String warpLocation = x + "," + y + "," + z + "," + yaw + "," + pitch;

            dataCache.updateWarpPoint(islandUuid, playerUuid, normalizedWarpName, warpLocation);

            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return CompletableFuture.supplyAsync(() -> {
            return dataCache.getIslandUuid(playerUuid);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            UUID islandUuid = islandUuidOpt.get();
            if (dataCache.getWarpLocation(islandUuid, playerUuid, warpName).isEmpty()) {
                throw new WarpDoesNotExistException();
            }

            dataCache.deleteWarpPoint(islandUuid, playerUuid, warpName);

            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> warp(UUID playerUuid, String warpName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return dataCache.getIslandUuid(playerUuid);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            if (dataCache.isPlayerBanned(islandUuid, targetPlayerUuid)) {
                throw new PlayerBannedException();
            }

            boolean isLocked = dataCache.isIslandLock(islandUuid);
            boolean isMember = dataCache.getIslandPlayers(islandUuid).contains(targetPlayerUuid);
            if (isLocked && !isMember) {
                throw new IslandLockedException();
            }

            Optional<String> warpLocationOpt = dataCache.getWarpLocation(islandUuid, playerUuid, warpName);
            if (warpLocationOpt.isEmpty()) {
                throw new WarpDoesNotExistException();
            }

            String warpWorld = IslandUtils.UUIDToName(islandUuid);
            String warpLocation = warpLocationOpt.get();

            return islandDistributor.teleportIsland(islandUuid, targetPlayerUuid, warpWorld, warpLocation);
        });
    }

    public CompletableFuture<Set<String>> getWarpNames(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UUID> islandUuidOpt = dataCache.getIslandUuid(playerUuid);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            return dataCache.getWarpNames(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}