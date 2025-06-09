package org.me.newsky.island;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.island.distributor.IslandServiceDistributor;

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

    public UUID getIslandUuid(UUID playerUuid) {
        return cacheHandler.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
    }

    public UUID getIslandOwner(UUID islandUuid) {
        return cacheHandler.getIslandOwner(islandUuid);
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        return cacheHandler.getIslandMembers(islandUuid);
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (cacheHandler.getIslandUuid(ownerUuid).isPresent()) {
                throw new IslandAlreadyExistException();
            }

            UUID islandUuid = UUID.randomUUID();
            String spawnLocation = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

            return islandServiceDistributor.createIsland(islandUuid, ownerUuid, spawnLocation);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(f -> f);
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> islandServiceDistributor.deleteIsland(islandUuid), plugin.getBukkitAsyncExecutor()).thenCompose(f -> f);
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

    public void sendPlayerMessage(UUID playerUuid, Component message) {
        CompletableFuture.runAsync(() -> islandServiceDistributor.sendPlayerMessage(playerUuid, message), plugin.getBukkitAsyncExecutor());
    }
}
