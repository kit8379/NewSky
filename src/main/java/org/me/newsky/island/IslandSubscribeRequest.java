package org.me.newsky.island;

import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class IslandSubscribeRequest {

    private final Logger logger;
    private final RedisHandler redisHandler;
    private final IslandOperation islandOperation;
    private final String serverID;

    private JedisPubSub requestSubscriber;

    public IslandSubscribeRequest(Logger logger, RedisHandler redisHandler, IslandOperation islandOperation, String serverID) {
        this.logger = logger;
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

                // Process the request
                processRequest(message).thenRun(() ->
                        // Send response back to the sender
                        redisHandler.publish("newsky-response-channel-" + requestID, serverID)
                );
            }
        };

        redisHandler.subscribe(requestSubscriber, "newsky-request-channel");
    }

    public void unsubscribeFromRequests() {
        if (requestSubscriber != null) {
            requestSubscriber.unsubscribe();
        }
    }

    private CompletableFuture<Void> processRequest(String message) {
        String[] parts = message.split(":");
        String operation = parts[2];

        // Extract additional data from the message
        String serverName = parts.length > 3 ? parts[3] : null;
        String worldName = parts.length > 4 ? parts[4] : null;
        String playerUuid = parts.length > 5 ? parts[5] : null;

        // Perform the operation based on the type
        switch (operation) {
            case "updateWorldList":
                return islandOperation.updateWorldList().thenRun(() -> logger.info(serverID + "updated the world list."));
            case "createIsland":
                if (serverName != null && serverName.equals(serverID)) {
                    return islandOperation.createWorld(worldName).thenRun(() -> logger.info("Island created successfully in " + serverID + ": " + worldName));
                }
                break;

            case "loadIsland":
                if (serverName != null && serverName.equals(serverID)) {
                    return islandOperation.loadWorld(worldName).thenRun(() -> logger.info("Island loaded successfully in " + serverID + ": " + worldName));
                }
                break;

            case "unloadIsland":
                if (serverName != null && serverName.equals(serverID)) {
                    return islandOperation.unloadWorld(worldName).thenRun(() -> logger.info("Island unloaded successfully in " + serverID + ": " + worldName));
                }
                break;

            case "deleteIsland":
                if (serverName != null && serverName.equals(serverID)) {
                    return islandOperation.deleteWorld(worldName).thenRun(() -> logger.info("Island deleted successfully in " + serverID + ": " + worldName));
                }
                break;

            case "teleportToIsland":
                if (serverName != null && serverName.equals(serverID)) {
                    return islandOperation.teleportToWorld(worldName, playerUuid).thenRun(() -> logger.info("Island teleported successfully to " + serverID + ": " + playerUuid + " to " + worldName));
                }
                break;

            default:
                logger.warning("Unknown operation: " + operation);
                break;
        }

        return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid parameters for operation: " + operation));
    }
}

