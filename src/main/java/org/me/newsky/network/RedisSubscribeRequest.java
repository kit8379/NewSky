package org.me.newsky.network;

import org.me.newsky.NewSky;
import org.me.newsky.island.middleware.PostIslandHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class RedisSubscribeRequest {

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final String serverID;
    private final PostIslandHandler postIslandHandler;
    private JedisPubSub requestSubscriber;

    public RedisSubscribeRequest(NewSky plugin, RedisHandler redisHandler, String serverID, PostIslandHandler postIslandHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
        this.postIslandHandler = postIslandHandler;
    }

    public void subscribeToRequestChannel() {
        requestSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String messageType = parts[0];
                String requestID = parts[1];
                String sourceServer = parts[2];
                String targetServer = parts[3];
                String operation = parts[4];

                String[] args = new String[parts.length - 5];
                System.arraycopy(parts, 5, args, 0, args.length);

                if (messageType.equals("request") && targetServer.equals(serverID)) {
                    plugin.debug("Received request from " + sourceServer + " for " + operation + " with request ID " + requestID);
                    processRequest(operation, args).thenRun(() -> {
                        String responseMessage = String.join(":", "response", "success", requestID, serverID, sourceServer);
                        redisHandler.publish("newsky-response-channel", responseMessage);
                        plugin.debug("Sent success response back to " + sourceServer + " for request ID " + requestID);
                    }).exceptionally(e -> {
                        plugin.getLogger().log(Level.SEVERE, "Failed to process request: " + e.getMessage());
                        String responseMessage = String.join(":", "response", "fail", requestID, serverID, sourceServer);
                        redisHandler.publish("newsky-response-channel", responseMessage);
                        plugin.debug("Sent failure response back to " + sourceServer + " for request ID " + requestID);
                        return null;
                    });
                }
            }
        };

        redisHandler.subscribe(requestSubscriber, "newsky-request-channel");
    }

    public void unsubscribeFromRequestChannel() {
        requestSubscriber.unsubscribe();
    }

    public CompletableFuture<Void> processRequest(String operation, String... args) {
        switch (operation) {
            case "create":
                String islandUuidForCreate = args[0];
                return postIslandHandler.createIsland(UUID.fromString(islandUuidForCreate));
            case "delete":
                String islandUuidForDelete = args[0];
                return postIslandHandler.deleteIsland(UUID.fromString(islandUuidForDelete));
            case "load":
                String islandUuidForLoad = args[0];
                return postIslandHandler.loadIsland(UUID.fromString(islandUuidForLoad));
            case "unload":
                String islandUuidForUnload = args[0];
                return postIslandHandler.unloadIsland(UUID.fromString(islandUuidForUnload));
            case "expel":
                String islandUuidForExpel = args[0];
                String playerUuidForExpel = args[1];
                return postIslandHandler.expelPlayer(UUID.fromString(islandUuidForExpel), UUID.fromString(playerUuidForExpel));
            case "lock":
                String islandUuidForLock = args[0];
                return postIslandHandler.lockIsland(UUID.fromString(islandUuidForLock));
            case "teleport":
                String islandUuidForTeleport = args[0];
                String playerUuidForTeleport = args[1];
                String locationStringForTeleport = args[2];
                return postIslandHandler.teleportToIsland(UUID.fromString(islandUuidForTeleport), UUID.fromString(playerUuidForTeleport), locationStringForTeleport);
            default:
                return CompletableFuture.failedFuture(new IllegalStateException("Unknown operation: " + operation));
        }
    }
}
