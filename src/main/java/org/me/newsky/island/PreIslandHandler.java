package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.network.BasePublishRequest;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles pre-island operations such as creating, deleting, loading, unloading, and locking islands.
 * This class is responsible for sending requests to the appropriate server to perform these operations.
 */
public class PreIslandHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final BasePublishRequest publishRequest;
    private final PostIslandHandler postIslandHandler;
    private final String serverID;

    public PreIslandHandler(NewSky plugin, CacheHandler cacheHandler, BasePublishRequest publishRequest, PostIslandHandler postIslandHandler, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.publishRequest = publishRequest;
        this.postIslandHandler = postIslandHandler;
        this.serverID = serverID;
    }


    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID playerUuid, String spawnLocation) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        publishRequest.sendRequest(getRandomServer(), "create", islandUuid.toString(), playerUuid.toString(), spawnLocation).thenAccept(result -> {
            future.complete(null);
            plugin.debug("Created island " + islandUuid + " on server " + serverID);
        });

        return future;
    }


    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        publishRequest.sendRequest(getServerByIsland(islandUuid.toString()), "delete", islandUuid.toString()).thenAccept(result -> {
            future.complete(null);
            plugin.debug("Deleted island " + islandUuid + " on server " + serverID);
        });

        return future;
    }


    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        publishRequest.sendRequest(getServerByIsland(islandUuid.toString()), "load", islandUuid.toString()).thenAccept(result -> {
            future.complete(null);
            plugin.debug("Loaded island " + islandUuid + " on server " + serverID);
        });

        return future;
    }


    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        publishRequest.sendRequest(getServerByIsland(islandUuid.toString()), "unload", islandUuid.toString()).thenAccept(result -> {
            future.complete(null);
            plugin.debug("Unloaded island " + islandUuid + " on server " + serverID);
        });

        return future;
    }


    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        publishRequest.sendRequest(getServerByIsland(islandUuid.toString()), "lock", islandUuid.toString()).thenAccept(result -> {
            future.complete(null);
            plugin.debug("Locked island " + islandUuid + " on server " + serverID);
        });

        return future;
    }


    public CompletableFuture<Void> teleportToIsland(UUID playerUuid, UUID islandUuid, String teleportLocation) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        publishRequest.sendRequest(getServerByIsland(islandUuid.toString()), "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenAccept(result -> {
            future.complete(null);
            plugin.debug("Teleported player " + playerUuid + " to island " + islandUuid + " on server " + serverID);
        });

        return future;
    }


    private String getRandomServer() {
        // Implement logic to choose a random or specific server
        return "skymain";
    }

    private String getServerByIsland(String islandUuid) {
        // Implement logic to choose the server where the island is loaded
        return "skymain";
    }
}
