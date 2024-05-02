package org.me.newsky.island;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.island.middleware.PreIslandHandler;

import java.util.Optional;
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

            cacheHandler.createIsland(islandUuid);
            cacheHandler.updateIslandPlayer(islandUuid, playerUuid, "owner");
            cacheHandler.updateHomePoint(islandUuid, playerUuid, "default", spawnLocation);

            return preIslandHandler.createIsland(islandUuid);
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

            cacheHandler.deleteIsland(islandUuid);

            return preIslandHandler.deleteIsland(islandUuid);
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

            return preIslandHandler.loadIsland(islandUuid);
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

            return preIslandHandler.unloadIsland(islandUuid);
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
                cacheHandler.updateIslandLock(islandUuid, true);
                preIslandHandler.lockIsland(islandUuid);
            } else {
                cacheHandler.updateIslandLock(islandUuid, false);
            }

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