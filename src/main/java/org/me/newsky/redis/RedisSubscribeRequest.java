package org.me.newsky.redis;

import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.JedisPubSub;

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
                processRequest(message);

                // Send response back to the sender
                redisHandler.publish("response-channel-" + senderID, serverID + ":" + requestID);
            }
        };

        redisHandler.subscribe(requestSubscriber, "request-channel");
    }

    public void unsubscribeFromRequests() {
        if (requestSubscriber != null) {
            requestSubscriber.unsubscribe("request-channel");
        }
    }

    private void processRequest(String message) {
        String[] parts = message.split(":");
        String operation = parts[2];

        // Assuming that the world name is provided after the operation in the message
        // For example: senderID:requestID:createWorld:worldName
        String serverName = parts.length > 3 ? parts[3] : null;
        String worldName = parts.length > 4 ? parts[4] : null;
        String playerName = parts.length > 5 ? parts[5] : null;

        switch (operation) {
            case "updateWorldList":
                redisOperation.updateWorldList(() -> {
                    // Logic after updateWorldList completes
                    logger.info("updateWorldList operation completed.");
                });
                break;
            case "createWorld":
                if (serverName == null || !(serverName.equals(serverID))) {
                    break;
                }

                if (worldName != null) {
                    redisOperation.createWorld(worldName, () -> logger.info("createWorld operation completed for world: " + worldName));
                } else {
                    logger.warning("World name not provided for createWorld operation.");
                }
                break;
            case "loadWorld":
                if (serverName == null || !(serverName.equals(serverID))) {
                    break;
                }

                if (worldName != null) {
                    redisOperation.loadWorld(worldName, () -> logger.info("loadWorld operation completed for world: " + worldName));
                } else {
                    logger.warning("World name not provided for loadWorld operation.");
                }
                break;
            case "unloadWorld":
                if (serverName == null || !(serverName.equals(serverID))) {
                    break;
                }

                if (worldName != null) {
                    redisOperation.unloadWorld(worldName, () -> logger.info("unloadWorld operation completed for world: " + worldName));
                } else {
                    logger.warning("World name not provided for unloadWorld operation.");
                }
                break;
            case "deleteWorld":
                if (serverName == null || !(serverName.equals(serverID))) {
                    break;
                }

                if (worldName != null) {
                    redisOperation.deleteWorld(worldName, () -> logger.info("deleteWorld operation completed for world: " + worldName));
                } else {
                    logger.warning("World name not provided for deleteWorld operation.");
                }
                break;
            case "teleportToWorld":
                if (serverName == null || !(serverName.equals(serverID))) {
                    break;
                }

                if (playerName == null) {
                    logger.warning("Player name not provided for teleportToWorld operation.");
                    break;
                }

                if (worldName != null) {
                    redisOperation.teleportToWorld(worldName, playerName, () -> logger.info("teleportToWorld operation completed for world: " + worldName));
                } else {
                    logger.warning("World name not provided for teleportToWorld operation.");
                }
            default:
                logger.warning("Unknown operation: " + operation);
        }
    }
}