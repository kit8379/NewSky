package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;

public class IslandSubscribeRequest {

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final IslandOperation islandOperation;
    private final String serverID;
    private JedisPubSub requestSubscriber;

    public IslandSubscribeRequest(NewSky plugin, RedisHandler redisHandler, IslandOperation islandOperation, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.islandOperation = islandOperation;
        this.serverID = serverID;
    }

    public void subscribeToRequests() {
        requestSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String requestID = parts[0];
                String targetServer = parts[2];

                if (targetServer.equals(serverID) || targetServer.equals("all")) {
                    plugin.debug("Received request: " + requestID + " for server: " + targetServer + " with operation: " + parts[3]);
                    processRequest(parts).thenAccept((String responseData) -> {
                        redisHandler.publish("newsky-response-channel-" + requestID, serverID + ":" + responseData);
                        plugin.debug("Sent response for request: " + requestID + " with data: " + responseData);
                    });
                }
            }
        };

        redisHandler.subscribe(requestSubscriber, "newsky-request-channel");
    }

    public void unsubscribeFromRequests() {
        if (requestSubscriber != null) {
            requestSubscriber.unsubscribe();
        }
    }

    private CompletableFuture<String> processRequest(String[] parts) {
        String operation = parts[3];

        switch (operation) {
            case "updateWorldList":
                return islandOperation.updateWorldList().thenApply((String updatedList) -> {
                    return updatedList;
                });

            case "createIsland":
                String worldNameForCreate = parts[4];
                return islandOperation.createWorld(worldNameForCreate).thenApply((Void v) -> {
                    return "Created";
                });

            case "deleteIsland":
                String worldNameForDelete = parts[4];
                return islandOperation.deleteWorld(worldNameForDelete).thenApply((Void v) -> {
                    return "Deleted";
                });

            case "teleportToIsland":
                String worldNameForTeleport = parts[4];
                String playerName = parts[5];
                String locationString = parts[6];
                return islandOperation.teleportToWorld(worldNameForTeleport, playerName, locationString).thenApply((Void v) -> {
                    return "Teleported";
                });

            default:
                return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown operation: " + operation));
        }
    }
}
