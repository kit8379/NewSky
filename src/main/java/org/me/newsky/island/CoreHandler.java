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

    /** SELF: a player may only create their own island. */
    public CompletableFuture<Void> createIsland(Actor actor, UUID ownerUuid) {
        actor.requireSelf(ownerUuid);
        return islandDistributor.createIsland(UUID.randomUUID(), ownerUuid);
    }

    /** OWNER, enforced in the delete transaction. */
    public CompletableFuture<Void> deleteIsland(Actor actor, UUID islandUuid) {
        return islandDistributor.deleteIsland(islandUuid, actor);
    }

    /** BYPASS: world placement is an operational concern, not a player-facing one. */
    public CompletableFuture<Void> loadIsland(Actor actor, UUID islandUuid) {
        actor.requireBypass();
        return islandDistributor.loadIsland(islandUuid);
    }

    /** BYPASS: world placement is an operational concern, not a player-facing one. */
    public CompletableFuture<Void> unloadIsland(Actor actor, UUID islandUuid) {
        actor.requireBypass();
        return islandDistributor.unloadIsland(islandUuid);
    }

    /** MEMBER, enforced in the toggle transaction. */
    public CompletableFuture<Boolean> toggleLock(Actor actor, UUID islandUuid) {
        return islandDistributor.toggleLock(islandUuid, actor);
    }

    /** MEMBER, enforced in the toggle transaction. */
    public CompletableFuture<Boolean> togglePvp(Actor actor, UUID islandUuid) {
        return islandDistributor.togglePvp(islandUuid, actor);
    }

    public CompletableFuture<Boolean> isIslandLock(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCore(islandUuid).map(DatabaseHandler.IslandCoreData::lock).orElse(false), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isIslandPvp(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCore(islandUuid).map(DatabaseHandler.IslandCoreData::pvp).orElse(false), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new), plugin.getBukkitAsyncExecutor());
    }
}
