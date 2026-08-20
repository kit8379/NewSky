package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.Actor;
import org.me.newsky.network.IslandDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BanHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;

    public BanHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> addBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        return islandDistributor.addBan(islandUuid, actor, playerUuid);
    }

    public CompletableFuture<Void> removeBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        return islandDistributor.removeBan(islandUuid, actor, playerUuid);
    }

    public CompletableFuture<Boolean> isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandBans(islandUuid).contains(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandBans(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandBans(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}
