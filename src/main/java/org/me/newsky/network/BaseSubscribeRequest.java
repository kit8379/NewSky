package org.me.newsky.network;

import org.me.newsky.NewSky;
import org.me.newsky.island.PostIslandHandler;
import org.me.newsky.redis.RedisHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseSubscribeRequest {

    protected final NewSky plugin;
    protected final RedisHandler redisHandler;
    protected final String serverID;
    protected final PostIslandHandler postIslandHandler;

    public BaseSubscribeRequest(NewSky plugin, RedisHandler redisHandler, String serverID, PostIslandHandler postIslandHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
        this.postIslandHandler = postIslandHandler;
    }

    /**
     * Subscribe to the request channel
     */
    public abstract void subscribeToRequestChannel();


    /**
     * Unsubscribe from the request channel
     */
    public abstract void unsubscribeFromRequestChannel();


    /**
     * Process a request
     *
     * @param operation The operation to perform
     * @param args      The arguments for the operation
     * @return A CompletableFuture that will be completed when the request is processed
     */
    public CompletableFuture<Void> processRequest(String operation, String... args) {
        switch (operation) {
            case "create":
                String islandUuidForCreate = args[0];
                String playerUuidForCreate = args[1];
                String locationStringForCreate = args[2];
                return postIslandHandler.createIsland(UUID.fromString(islandUuidForCreate), UUID.fromString(playerUuidForCreate), locationStringForCreate);
            case "delete":
                String islandUuidForDelete = args[0];
                return postIslandHandler.deleteIsland(UUID.fromString(islandUuidForDelete));
            case "load":
                String islandUuidForLoad = args[0];
                return postIslandHandler.loadIsland(UUID.fromString(islandUuidForLoad));
            case "unload":
                String islandUuidForUnload = args[0];
                return postIslandHandler.unloadIsland(UUID.fromString(islandUuidForUnload));
            case "teleport":
                String islandUuidForTeleport = args[0];
                String playerUuidForTeleport = args[1];
                String locationStringForTeleport = args[2];
                return postIslandHandler.teleportToIsland(UUID.fromString(islandUuidForTeleport), UUID.fromString(playerUuidForTeleport), locationStringForTeleport);
            case "lock":
                String islandUuidForLock = args[0];
                return postIslandHandler.lockIsland(UUID.fromString(islandUuidForLock));
            default:
                return CompletableFuture.failedFuture(new IllegalStateException("Unknown operation: " + operation));
        }
    }
}
