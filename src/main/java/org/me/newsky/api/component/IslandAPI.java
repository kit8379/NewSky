package org.me.newsky.api.component;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.island.PreIslandHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandAPI {


    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;

    public IslandAPI(ConfigHandler config, CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
    }

    public CompletableFuture<Void> createIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {

            // Check if the player already has an island
            if (islandUuidOpt.isPresent()) {
                throw new IslandAlreadyExistException();
            }

            // Create a new island using a random UUID
            UUID islandUuid = UUID.randomUUID();
            String spawnLocation = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

            preIslandHandler.createIsland(islandUuid, playerUuid, spawnLocation);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {

            // Check if the player has an island
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            preIslandHandler.deleteIsland(islandUuid);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {

            // Check if the player has an island
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            preIslandHandler.loadIsland(islandUuid);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {

            // Check if the player has an island
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            preIslandHandler.unloadIsland(islandUuid);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(playerUuid);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            boolean isLocked = cacheHandler.getIslandLock(islandUuid);
            if (!isLocked) {
                preIslandHandler.lockIsland(islandUuid);
            }
            cacheHandler.updateIslandLock(islandUuid, !isLocked);
            return !isLocked;
        });
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(playerUuid);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            boolean isPvpEnabled = cacheHandler.getIslandPvp(islandUuid);
            cacheHandler.updateIslandPvp(islandUuid, !isPvpEnabled);
            return !isPvpEnabled;
        });
    }

}
