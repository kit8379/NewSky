package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.network.distributor.Distributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final NewSky plugin;
    private final Cache cache;
    private final Distributor distributor;

    public IslandHandler(NewSky plugin, Cache cache, Distributor distributor) {
        this.plugin = plugin;
        this.cache = cache;
        this.distributor = distributor;
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (cache.getIslandUuid(ownerUuid).isPresent()) {
                throw new IslandAlreadyExistException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            UUID islandUuid = UUID.randomUUID();
            cache.createIsland(islandUuid, ownerUuid);
            return distributor.createIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> cache.deleteIsland(islandUuid), plugin.getBukkitAsyncExecutor()).thenCompose(v -> distributor.deleteIsland(islandUuid));
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> distributor.loadIsland(islandUuid));
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> distributor.unloadIsland(islandUuid));
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> cache.isIslandLock(islandUuid), plugin.getBukkitAsyncExecutor()).thenCompose(isLocked -> {
            if (isLocked) {
                return CompletableFuture.runAsync(() -> cache.updateIslandLock(islandUuid, false), plugin.getBukkitAsyncExecutor()).thenApply(v -> false); // return !isLocked
            } else {
                return distributor.lockIsland(islandUuid).thenRunAsync(() -> cache.updateIslandLock(islandUuid, true), plugin.getBukkitAsyncExecutor()).thenApply(v -> true); // return !isLocked
            }
        });
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isPvpEnabled = cache.isIslandPvp(islandUuid);
            cache.updateIslandPvp(islandUuid, !isPvpEnabled);
            return !isPvpEnabled;
        }, plugin.getBukkitAsyncExecutor());
    }

    public boolean isIslandLock(UUID islandUuid) {
        return cache.isIslandLock(islandUuid);
    }

    public boolean isIslandPvp(UUID islandUuid) {
        return cache.isIslandPvp(islandUuid);
    }

    public UUID getIslandUuid(UUID playerUuid) {
        return cache.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
    }
}
