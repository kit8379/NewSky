package org.me.newsky.island;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.island.middleware.PreIslandHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;

    public IslandHandler(ConfigHandler configHandler, CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.config = configHandler;
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
    }

    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
        });
    }

    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandOwner(islandUuid);
        });
    }

    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandMembers(islandUuid);
        });
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        if (cacheHandler.getIslandUuid(ownerUuid).isPresent()) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IslandAlreadyExistException());
            return future;
        }

        UUID islandUuid = UUID.randomUUID();
        String spawnLocation = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();
        cacheHandler.createIsland(islandUuid);
        cacheHandler.updateIslandPlayer(islandUuid, ownerUuid, "owner");
        cacheHandler.updateHomePoint(islandUuid, ownerUuid, "default", spawnLocation);

        return preIslandHandler.createIsland(islandUuid);
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        cacheHandler.deleteIsland(islandUuid);
        return preIslandHandler.deleteIsland(islandUuid);
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return preIslandHandler.loadIsland(islandUuid);
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return preIslandHandler.unloadIsland(islandUuid);
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        boolean isLocked = cacheHandler.getIslandLock(islandUuid);
        cacheHandler.updateIslandLock(islandUuid, !isLocked);
        if (!isLocked) {
            preIslandHandler.lockIsland(islandUuid);
        }
        return CompletableFuture.completedFuture(!isLocked);
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        boolean isPvpEnabled = cacheHandler.getIslandPvp(islandUuid);
        cacheHandler.updateIslandPvp(islandUuid, !isPvpEnabled);
        return CompletableFuture.completedFuture(!isPvpEnabled);
    }
}
