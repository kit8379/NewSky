package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerNotCoopedException;
import org.me.newsky.model.Actor;
import org.me.newsky.network.IslandDistributor;

import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class CoopHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public CoopHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor,
                       OnlinePlayerRegistry onlinePlayerRegistry) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    public CompletableFuture<Void> addCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> onlinePlayerRegistry.requireOnline(playerUuid),
                plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandDistributor.addCoop(islandUuid, actor, playerUuid);
        });
    }

    public CompletableFuture<Void> removeCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return islandDistributor.removeCoop(islandUuid, actor, playerUuid);
    }

    public CompletableFuture<Void> removeAllCoops(UUID playerUuid) {
        Actor cleanup = new Actor.Bypass("system");

        return CompletableFuture.supplyAsync(() -> database.getCoopIslands(playerUuid),
                plugin.getBukkitAsyncExecutor()).thenCompose(islands -> {
            List<CompletableFuture<Void>> removals = new ArrayList<>();

            for (UUID islandUuid : islands) {
                CompletableFuture<Void> removal = islandDistributor
                        .removeCoop(islandUuid, cleanup, playerUuid)
                        .exceptionallyCompose(error -> handleCleanupFailure(playerUuid, islandUuid, error));
                removals.add(removal);
            }

            return CompletableFuture.allOf(removals.toArray(CompletableFuture[]::new));
        });
    }

    private CompletableFuture<Void> handleCleanupFailure(UUID playerUuid, UUID islandUuid,
                                                          Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof PlayerNotCoopedException || cause instanceof IslandDoesNotExistException) {
            return CompletableFuture.completedFuture(null);
        }

        plugin.severe("Failed to remove coop of " + playerUuid + " on island " + islandUuid, error);
        return CompletableFuture.failedFuture(error);
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCoops(islandUuid).contains(playerUuid),
                plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandCoops(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCoops(islandUuid),
                plugin.getBukkitAsyncExecutor());
    }
}
