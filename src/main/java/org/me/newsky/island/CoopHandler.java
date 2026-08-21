package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerNotCoopedException;
import org.me.newsky.model.Actor;
import org.me.newsky.network.IslandDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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

    /**
     * MEMBER, enforced in the coop transaction.
     */
    public CompletableFuture<Void> addCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> onlinePlayerRegistry.requireOnline(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.addCoop(islandUuid, actor, playerUuid));
    }

    /**
     * MEMBER, enforced in the uncoop transaction.
     */
    public CompletableFuture<Void> removeCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return islandDistributor.removeCoop(islandUuid, actor, playerUuid);
    }

    /**
     * Quit cleanup: every coop this player holds is removed as a normal routed write, so each
     * removal lands on its island's claim holder and applies its delta there - the same guaranteed
     * path as any other write, with no snapshot refresh machinery. Removals that lost a race
     * (coop already gone, island deleted meanwhile) count as done; other failures are logged and
     * do not stop the rest.
     */
    public CompletableFuture<Void> removeAllCoops(UUID playerUuid) {
        Actor cleanup = new Actor.Bypass("system");

        return CompletableFuture.supplyAsync(() -> database.getCoopIslands(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islands -> {
            CompletableFuture<?>[] removals = islands.stream().map(islandUuid -> islandDistributor.removeCoop(islandUuid, cleanup, playerUuid).exceptionallyCompose(error -> {
                Throwable cause = error;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                if (cause instanceof PlayerNotCoopedException || cause instanceof IslandDoesNotExistException) {
                    return CompletableFuture.completedFuture(null);
                }
                plugin.severe("Failed to remove coop of " + playerUuid + " on island " + islandUuid + " during quit cleanup", error);
                return CompletableFuture.failedFuture(error);
            })).toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(removals);
        });
    }

    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCoops(islandUuid).contains(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandCoops(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCoops(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}
