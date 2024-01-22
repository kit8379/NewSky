package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

public class IslandPublishRequest {

    private static final long TIMEOUT_SECONDS = 30L;
    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final String serverID;

    public IslandPublishRequest(NewSky plugin, RedisHandler redisHandler, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> sendRequest(String targetServer, String operation) {
        operation = targetServer + ":" + operation;
        String requestID = "Req-" + UUID.randomUUID();

        CompletableFuture<Void> future = new CompletableFuture<>();

        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                plugin.debug("Received response from " + message + " for request " + requestID);

                // Unsubscribe from the response channel
                this.unsubscribe();
                plugin.debug("Unsubscribed from response channel for request: " + requestID);

                future.complete(null);
            }
        };

        // Subscribe to the response channel
        redisHandler.subscribe(responseSubscriber, "newsky-response-channel-" + requestID);
        plugin.debug("Subscribed to response channel for request: " + requestID);

        // Send request
        redisHandler.publish("newsky-request-channel", requestID + ":" + serverID + ":" + operation);
        plugin.debug("Sent request " + requestID + "to request channel for " + targetServer);
        scheduleTimeoutTask(future, requestID, responseSubscriber);
        return future;
    }

    private void scheduleTimeoutTask(CompletableFuture<Void> future, String requestID, JedisPubSub responseSubscriber) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!future.isDone()) {
                plugin.info("Request " + requestID + " timed out.");

                // Unsubscribe from the response channel
                responseSubscriber.unsubscribe();
                plugin.debug("Unsubscribed from response channel due to timeout for request: " + requestID);

                future.completeExceptionally(new TimeoutException("Timeout waiting for responses to request:" + requestID));

            }
        }, TIMEOUT_SECONDS * 20);
    }
}
