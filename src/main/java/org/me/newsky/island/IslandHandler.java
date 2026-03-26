package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.network.IslandDistributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final DataCache dataCache;
    private final IslandDistributor islandDistributor;

    public IslandHandler(NewSky plugin, ConfigHandler config, DataCache dataCache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.config = config;
        this.dataCache = dataCache;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (dataCache.getIslandUuid(ownerUuid).isPresent()) {
                throw new IslandAlreadyExistException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            UUID islandUuid = UUID.randomUUID();
            String homeLocation = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

            dataCache.createIsland(islandUuid, ownerUuid, homeLocation);
            return islandDistributor.createIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandDistributor.deleteIsland(islandUuid);
        }).thenRun(() -> {
            dataCache.deleteIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandDistributor.loadIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandDistributor.unloadIsland(islandUuid);
        });
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.isIslandLock(islandUuid), plugin.getBukkitAsyncExecutor()).thenApplyAsync(isLocked -> {
            if (isLocked) {
                dataCache.updateIslandLock(islandUuid, false);
                islandDistributor.reloadSnapshot(islandUuid);
                return false;
            } else {
                dataCache.updateIslandLock(islandUuid, true);
                islandDistributor.lockIsland(islandUuid);
                islandDistributor.reloadSnapshot(islandUuid);
                return true;
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean enabled = dataCache.isIslandPvp(islandUuid);
            boolean newValue = !enabled;
            dataCache.updateIslandPvp(islandUuid, newValue);
            islandDistributor.reloadSnapshot(islandUuid);
            return newValue;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return dataCache.isIslandLock(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.isIslandPvp(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new), plugin.getBukkitAsyncExecutor());
    }
}