package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.island.middleware.PreIslandHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;

    public IslandHandler(NewSky plugin, ConfigHandler configHandler, CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.plugin = plugin;
        this.config = configHandler;
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
    }

    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandOwner(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandMembers(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (cacheHandler.getIslandUuid(ownerUuid).isPresent()) {
                throw new IslandAlreadyExistException();
            }

            UUID islandUuid = UUID.randomUUID();
            String spawnLocation = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();
            cacheHandler.createIsland(islandUuid);
            cacheHandler.updateIslandPlayer(islandUuid, ownerUuid, "owner");
            cacheHandler.updateHomePoint(islandUuid, ownerUuid, "default", spawnLocation);

            return preIslandHandler.createIsland(islandUuid);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(voidFuture -> voidFuture);
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> {
            cacheHandler.deleteIsland(islandUuid);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(voidFuture -> preIslandHandler.deleteIsland(islandUuid));
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return preIslandHandler.loadIsland(islandUuid);
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return preIslandHandler.unloadIsland(islandUuid);
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isLocked = cacheHandler.getIslandLock(islandUuid);
            cacheHandler.updateIslandLock(islandUuid, !isLocked);
            if (!isLocked) {
                preIslandHandler.lockIsland(islandUuid);
            }
            return !isLocked;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isPvpEnabled = cacheHandler.getIslandPvp(islandUuid);
            cacheHandler.updateIslandPvp(islandUuid, !isPvpEnabled);
            return !isPvpEnabled;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            preIslandHandler.expelPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}
