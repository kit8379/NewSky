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

    /** MEMBER, enforced in the coop transaction. */
    public CompletableFuture<Void> coopPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        // Coop grants trust to someone currently visiting, so it only applies to online players.
        return CompletableFuture.runAsync(() -> onlinePlayerRegistry.requireOnline(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.addCoop(islandUuid, actor, playerUuid));
    }

    /** MEMBER, enforced in the uncoop transaction. */
    public CompletableFuture<Void> unCoopPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return islandDistributor.removeCoop(islandUuid, actor, playerUuid);
    }

    /**
     * BYPASS: wiping every coop a player holds spans islands the actor has no role on, so it is
     * an internal cleanup task (run when they disconnect), never a player-facing operation.
     */
    public CompletableFuture<Void> deleteAllCoopOfPlayer(Actor actor, UUID playerUuid) {
        actor.requireBypass();

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
