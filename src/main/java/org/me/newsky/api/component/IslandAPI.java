package org.me.newsky.api.component;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandAPI {

    private final CacheHandler cacheHandler;
    private final IslandHandler islandHandler;

    public IslandAPI(CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public CompletableFuture<Void> createIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isPresent()) {
                throw new IslandAlreadyExistException();
            }
            UUID islandUuid = UUID.randomUUID();
            // Set the island spawn location based on your plugin's configuration
            String spawnLocation = "x,y,z,yaw,pitch"; // Example format
            islandHandler.createIsland(islandUuid, playerUuid, spawnLocation);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            islandHandler.deleteIsland(islandUuid);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            islandHandler.loadIsland(islandUuid);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            islandHandler.unloadIsland(islandUuid);
            return CompletableFuture.completedFuture(null);
        });
    }
}
