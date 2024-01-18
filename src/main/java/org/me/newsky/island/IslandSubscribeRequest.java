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
        String playerName = parts.length > 5 ? parts[5] : null;

        // Perform the operation based on the type
        switch (operation) {
            case "updateWorldList":
                return islandOperation.updateWorldList()
                        .thenRun(() -> logger.info("updateWorldList operation completed."));
            case "createIsland":
                if (serverName != null && serverName.equals(serverID) && worldName != null) {
                    return islandOperation.createWorld(worldName)
                            .thenRun(() -> logger.info("createIsland operation completed for world: " + worldName));
                }
                break;
            case "loadIsland":
                if (serverName != null && serverName.equals(serverID) && worldName != null) {
                    return islandOperation.loadWorld(worldName)
                            .thenRun(() -> logger.info("loadIsland operation completed for world: " + worldName));
                }
                break;
            case "unloadIsland":
                if (serverName != null && serverName.equals(serverID) && worldName != null) {
                    return islandOperation.unloadWorld(worldName)
                            .thenRun(() -> logger.info("unloadIsland operation completed for world: " + worldName));
                }
                break;
            case "deleteIsland":
                if (serverName != null && serverName.equals(serverID) && worldName != null) {
                    return islandOperation.deleteWorld(worldName)
                            .thenRun(() -> logger.info("deleteIsland operation completed for world: " + worldName));
                }
                break;
            case "teleportToIsland":
                if (serverName != null && serverName.equals(serverID) && playerName != null && worldName != null) {
                    return islandOperation.teleportToWorld(worldName, playerName)
                            .thenRun(() -> logger.info("teleportToIsland operation completed for world: " + worldName));
                }
                break;
            default:
                return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.completedFuture(null);
    }
}

