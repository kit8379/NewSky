package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.network.IslandDistributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoreHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;

    public CoreHandler(NewSky plugin, ConfigHandler config, DatabaseHandler database, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (database.getIslandUuid(ownerUuid).isPresent()) {
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
        return islandDistributor.toggleIslandLock(islandUuid);
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return islandDistributor.toggleIslandPvp(islandUuid);
    }

    public CompletableFuture<Boolean> isIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return database.getIslandCore(islandUuid).map(DatabaseHandler.IslandCoreData::lock).orElse(false);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return database.getIslandCore(islandUuid).map(DatabaseHandler.IslandCoreData::pvp).orElse(false);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
        }, plugin.getBukkitAsyncExecutor());
    }
}
