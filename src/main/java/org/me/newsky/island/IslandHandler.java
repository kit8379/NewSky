package org.me.newsky.island;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerDoesNotExistException;
import org.me.newsky.island.distributor.IslandDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final NewSky plugin;
    private final Cache cache;
    private final IslandDistributor islandDistributor;

    public IslandHandler(NewSky plugin, Cache cache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.cache = cache;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (cache.getIslandUuid(ownerUuid).isPresent()) {
                throw new IslandAlreadyExistException();
            }
            UUID islandUuid = UUID.randomUUID();
            cache.createIsland(islandUuid, ownerUuid);
            return islandDistributor.createIsland(islandUuid);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(f -> f);
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            cache.deleteIsland(islandUuid);
            return islandDistributor.deleteIsland(islandUuid);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(f -> f);
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> islandUuid, plugin.getBukkitAsyncExecutor()).thenCompose(islandDistributor::loadIsland);
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> islandUuid, plugin.getBukkitAsyncExecutor()).thenCompose(islandDistributor::unloadIsland);
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isLocked = cache.isIslandLock(islandUuid);
            if (!isLocked) {
                islandDistributor.lockIsland(islandUuid);
            }
            cache.updateIslandLock(islandUuid, !isLocked);
            return !isLocked;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isPvpEnabled = cache.isIslandPvp(islandUuid);
            cache.updateIslandPvp(islandUuid, !isPvpEnabled);
            return !isPvpEnabled;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> players = cache.getIslandPlayers(islandUuid);
            if (!players.contains(playerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }
            islandDistributor.expelPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public void sendMessage(UUID playerUuid, Component message) {
        CompletableFuture.runAsync(() -> islandDistributor.sendMessage(playerUuid, message), plugin.getBukkitAsyncExecutor());
    }

    public UUID getIslandUuid(UUID playerUuid) {
        return cache.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
    }

    public UUID getIslandOwner(UUID islandUuid) {
        return cache.getIslandOwner(islandUuid);
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        return cache.getIslandMembers(islandUuid);
    }
}
