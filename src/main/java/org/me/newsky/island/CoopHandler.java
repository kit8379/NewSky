package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Actor;
import org.me.newsky.network.IslandDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoopHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public CoopHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor, OnlinePlayerRegistry onlinePlayerRegistry) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    public CompletableFuture<Void> coopPlayer(UUID islandUuid, Actor actor, UUID playerUuid) {
        // Coop grants trust to someone currently visiting, so it only applies to online players.
        return CompletableFuture.runAsync(() -> onlinePlayerRegistry.requireOnline(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.addCoop(islandUuid, actor, playerUuid));
    }

    public CompletableFuture<Void> unCoopPlayer(UUID islandUuid, Actor actor, UUID playerUuid) {
        return islandDistributor.removeCoop(islandUuid, actor, playerUuid);
    }

    public CompletableFuture<Void> deleteAllCoopOfPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.deleteAllCoopsOfPlayer(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(touchedIslands -> {
            CompletableFuture<?>[] refreshes = touchedIslands.stream().map(islandDistributor::refreshIslandSnapshot).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(refreshes);
        });
    }

    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCoops(islandUuid).contains(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getCoopedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCoops(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}
