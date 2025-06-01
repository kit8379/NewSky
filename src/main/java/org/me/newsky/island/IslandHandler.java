package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandCreationInProgressException;
import org.me.newsky.exceptions.IslandDeletionInProgressException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.island.middleware.IslandServiceDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final IslandServiceDistributor islandServiceDistributor;

    public IslandHandler(NewSky plugin, ConfigHandler configHandler, CacheHandler cacheHandler, IslandServiceDistributor islandServiceDistributor) {
        this.plugin = plugin;
        this.config = configHandler;
        this.cacheHandler = cacheHandler;
        this.islandServiceDistributor = islandServiceDistributor;
    }

    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandOwner(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandMembers(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (cacheHandler.getIslandUuid(ownerUuid).isPresent()) {
                throw new IslandAlreadyExistException();
            }

            String lockKey = "lock:island_create:" + ownerUuid;
            if (!cacheHandler.acquireLock(lockKey, 30)) {
                throw new IslandCreationInProgressException();
            }

            UUID islandUuid = UUID.randomUUID();
            String spawnLocation = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

            return islandServiceDistributor.createIsland(islandUuid, ownerUuid, spawnLocation).whenComplete((res, ex) -> cacheHandler.releaseLock(lockKey));

        }, plugin.getBukkitAsyncExecutor()).thenCompose(f -> f);
    }


    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String lockKey = "lock:island_delete:" + islandUuid;
            if (!cacheHandler.acquireLock(lockKey, 30)) {
                throw new IslandDeletionInProgressException();
            }

            return islandServiceDistributor.deleteIsland(islandUuid).whenComplete((res, ex) -> cacheHandler.releaseLock(lockKey));
        }, plugin.getBukkitAsyncExecutor()).thenCompose(f -> f);
    }


    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> islandUuid, plugin.getBukkitAsyncExecutor()).thenCompose(islandServiceDistributor::loadIsland);
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> islandUuid, plugin.getBukkitAsyncExecutor()).thenCompose(islandServiceDistributor::unloadIsland);
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isLocked = cacheHandler.isIslandLock(islandUuid);
            cacheHandler.updateIslandLock(islandUuid, !isLocked);
            if (!isLocked) {
                islandServiceDistributor.lockIsland(islandUuid);
            }
            return !isLocked;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isPvpEnabled = cacheHandler.isIslandPvp(islandUuid);
            cacheHandler.updateIslandPvp(islandUuid, !isPvpEnabled);
            return !isPvpEnabled;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> islandServiceDistributor.expelPlayer(islandUuid, playerUuid), plugin.getBukkitAsyncExecutor());
    }
}
