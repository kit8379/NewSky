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
                String receiverID = parts[2];

                if(!receiverID.equals(serverID)) {
                    return;
                }

                // Process the request
                processRequest(message).thenRun(() ->
                        // Send response
                        {
                            redisHandler.publish("newsky-response-channel-" + requestID, serverID);
                            plugin.debug("Sent response back to request " + requestID + " to response channel.");
                        }

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
        String operation = parts[3];

        // Extract additional data from the message
        String worldName = parts.length > 4 ? parts[4] : null;
        String playerName = parts.length > 5 ? parts[5] : null;

        // Perform the operation based on the type
        if ("createIsland".equals(operation)) {
            return islandOperation.createWorld(worldName)
                    .thenRun(() -> {
                        plugin.debug("createIsland operation completed for world: " + worldName);
                    });
        }
        if ("deleteIsland".equals(operation)) {
            return islandOperation.deleteWorld(worldName)
                    .thenRun(() -> {
                        plugin.debug("deleteIsland operation completed for world: " + worldName);
                    });
        }
        if ("teleportToIsland".equals(operation)) {
            return islandOperation.teleportToWorld(worldName, playerName)
                    .thenRun(() -> {
                        plugin.debug("teleportToIsland operation completed for world: " + worldName);
                    });
        }
        return CompletableFuture.completedFuture(null);
    }
}

