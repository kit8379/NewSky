package org.me.newsky.island.post;

import org.me.newsky.NewSky;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandPublishRequest {
    private static final long TIMEOUT_SECONDS = 30L;
    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final HeartBeatHandler heartBeatHandler;
    private final String serverID;

    public IslandPublishRequest(NewSky plugin, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<ConcurrentHashMap<String, String>> sendRequest(String targetServer, String operation) {
        CompletableFuture<ConcurrentHashMap<String, String>> future = new CompletableFuture<>();

        // Create a unique request ID
        String requestID = "Req-" + UUID.randomUUID();

        // Create a ConcurrentHashMap to store the responses
        ConcurrentHashMap<String, String> responses = new ConcurrentHashMap<>();

        // Get the active servers
        Set<String> activeServers = heartBeatHandler.getActiveServers();

        // Create a subscriber for the response channel
        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] responseParts = message.split(":");
                String responderID = responseParts[0];
                String responseState = responseParts[1];
                String responseData = responseParts[2];

                if (!responseState.equals("Success")) {
                    this.unsubscribe();
                    future.completeExceptionally(new IllegalStateException(responseData));
                    plugin.debug("Received error response from " + responderID + " for request " + requestID + ": " + responseData);
                    return;
                }

                // Store the response in the map
                responses.put(responderID, responseData);
                plugin.debug("Received response from " + responderID + " for request " + requestID + ": " + responseData);

                // Check if all servers have responded or the target server has responded to the request and complete the future
                boolean shouldComplete = (targetServer.equals("all") && responses.keySet().containsAll(activeServers)) || responderID.equals(targetServer);
                if (shouldComplete) {
                    this.unsubscribe();
                    future.complete(responses);
                    plugin.debug("Completed request " + requestID);
                }
            }
        };

        // Subscribe to the response channel
        redisHandler.subscribe(responseSubscriber, "newsky-response-channel-" + requestID);
        plugin.debug("Subscribed to response channel for request " + requestID);

        // Publish the request
        redisHandler.publish("newsky-request-channel", requestID + ":" + serverID + ":" + targetServer + ":" + operation);
        plugin.debug("Published request " + requestID + " to " + targetServer + " for operation " + operation);

        // Schedule a timeout task
        scheduleTimeoutTask(future, requestID, responseSubscriber);
        return future;
    }

    private void scheduleTimeoutTask(CompletableFuture<ConcurrentHashMap<String, String>> future, String requestID, JedisPubSub responseSubscriber) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!future.isDone()) {
                responseSubscriber.unsubscribe();
                future.completeExceptionally(new IllegalStateException("Timed out waiting for response to request " + requestID));
                plugin.debug("Timed out waiting for response to request " + requestID);
            }
        }, TIMEOUT_SECONDS * 20);
    }
}
