package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.network.IslandDistributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoreHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final DataCache dataCache;
    private final IslandDistributor islandDistributor;

    public CoreHandler(NewSky plugin, ConfigHandler config, DataCache dataCache, IslandDistributor islandDistributor) {
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

            return islandDistributor.createIsland(islandUuid, ownerUuid, homeLocation);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandDistributor.deleteIsland(islandUuid);
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
        return CompletableFuture.supplyAsync(() -> {
            boolean enabled = dataCache.isIslandLock(islandUuid);
            return !enabled;
        }, plugin.getBukkitAsyncExecutor()).thenCompose(newValue -> {
            return islandDistributor.setIslandLock(islandUuid, newValue).thenApply(v -> newValue);
        });
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean enabled = dataCache.isIslandPvp(islandUuid);
            return !enabled;
        }, plugin.getBukkitAsyncExecutor()).thenCompose(newValue -> {
            return islandDistributor.setIslandPvp(islandUuid, newValue).thenApply(v -> newValue);
        });
    }

    public CompletableFuture<Boolean> isIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return dataCache.isIslandLock(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return dataCache.isIslandPvp(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return dataCache.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
        }, plugin.getBukkitAsyncExecutor());
    }
}
