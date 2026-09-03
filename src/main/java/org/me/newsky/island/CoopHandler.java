package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.PlayerNotOnlineException;
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

    public CompletableFuture<Void> coopPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!onlinePlayerRegistry.isOnline(playerUuid)) {
                throw new PlayerNotOnlineException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandDistributor.addCoop(actor, islandUuid, playerUuid);
        });
    }

    public CompletableFuture<Void> unCoopPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.removeCoop(actor, islandUuid, playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> deleteAllCoopOfPlayer(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> database.deleteAllCoopsOfPlayer(playerUuid),
                plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCoops(islandUuid).contains(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getCoopedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCoops(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}
