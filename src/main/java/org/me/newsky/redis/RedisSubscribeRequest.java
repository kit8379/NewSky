package org.me.newsky.redis;

import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class RedisSubscribeRequest {

    private final Logger logger;
    private final RedisHandler redisHandler;
    private final RedisOperation redisOperation;
    private final String serverID;

    private JedisPubSub requestSubscriber;

    public RedisSubscribeRequest(Logger logger, ConfigHandler config, RedisHandler redisHandler, RedisOperation redisOperation) {
        this.logger = logger;
        this.redisHandler = redisHandler;
        this.redisOperation = redisOperation;
        this.serverID = config.getServerName();
    }

    public void subscribeToRequests() {
        requestSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String senderID = parts[0];
                String requestID = parts[1];

                // Process the request
                processRequest(message).thenRun(() ->
                        // Send response back to the sender
                        redisHandler.publish("newsky-response-channel-" + senderID, serverID + ":" + requestID)
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
                return redisOperation.updateWorldList()
                        .thenRun(() -> logger.info("updateWorldList operation completed."));
            case "createIsland":
                if (serverName != null && serverName.equals(serverID) && worldName != null) {
                    return redisOperation.createWorld(worldName)
                            .thenRun(() -> logger.info("createIsland operation completed for world: " + worldName));
                }
                break;
            case "loadIsland":
                if (serverName != null && serverName.equals(serverID) && worldName != null) {
                    return redisOperation.loadWorld(worldName)
                            .thenRun(() -> logger.info("loadIsland operation completed for world: " + worldName));
                }
                break;
            case "unloadIsland":
                if (serverName != null && serverName.equals(serverID) && worldName != null) {
                    return redisOperation.unloadWorld(worldName)
                            .thenRun(() -> logger.info("unloadIsland operation completed for world: " + worldName));
                }
                break;
            case "deleteIsland":
                if (serverName != null && serverName.equals(serverID) && worldName != null) {
                    return redisOperation.deleteWorld(worldName)
                            .thenRun(() -> logger.info("deleteIsland operation completed for world: " + worldName));
                }
                break;
            case "teleportToIsland":
                if (serverName != null && serverName.equals(serverID) && playerName != null && worldName != null) {
                    return redisOperation.teleportToWorld(playerName, worldName)
                            .thenRun(() -> logger.info("teleportToIsland operation completed for world: " + worldName));
                }
                break;
            default:
                logger.warning("Unknown operation: " + operation);
                return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown operation: " + operation));
        }

        return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid parameters for operation: " + operation));
    }
}

