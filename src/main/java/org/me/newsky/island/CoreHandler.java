package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.model.Actor;
import org.me.newsky.network.IslandDistributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoreHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;

    public CoreHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> createIsland(UUID ownerUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.createIsland(UUID.randomUUID(), ownerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> deleteIsland(Actor actor, UUID islandUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.deleteIsland(actor, islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.loadIsland(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.unloadIsland(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> toggleIslandLock(Actor actor, UUID islandUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.toggleIslandLock(actor, islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> toggleIslandPvp(Actor actor, UUID islandUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.toggleIslandPvp(actor, islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.isIslandLock(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.isIslandPvp(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new), plugin.getBukkitAsyncExecutor());
    }
}
