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

                // Check if the target server matches this server or if the request is for all servers
                if (targetServer.equals(serverID) || targetServer.equals("all")) {
                    processRequest(parts).thenAccept(responseData -> {
                        redisHandler.publish("newsky-response-channel-" + requestID, serverID + ":" + responseData);
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
            case "createIsland":
                String worldNameForCreate = parts.length > 4 ? parts[4] : null;
                return islandOperation.createWorld(worldNameForCreate)
                        .thenApply(v -> {
                            plugin.debug("createIsland operation completed for world: " + worldNameForCreate);
                            return "Created";
                        });

            case "deleteIsland":
                String worldNameForDelete = parts.length > 4 ? parts[4] : null;
                return islandOperation.deleteWorld(worldNameForDelete)
                        .thenApply(v -> {
                            plugin.debug("deleteIsland operation completed for world: " + worldNameForDelete);
                            return "Deleted";
                        });

            case "updateWorldList":
                return islandOperation.updateWorldList()
                        .thenApply(updatedList -> {
                            plugin.debug("updateWorldList operation completed.");
                            return "Updated";
                        });
            default:
                return CompletableFuture.completedFuture("Unknown operation: " + operation);
        }
    }
}
