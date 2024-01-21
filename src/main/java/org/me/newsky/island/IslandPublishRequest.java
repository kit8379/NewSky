package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class IslandPublishRequest {

    private static final long TIMEOUT_SECONDS = 30L;
    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final HeartBeatHandler heartBeatHandler;
    private final String serverID;
    private final Set<String> serversToWaitFor = ConcurrentHashMap.newKeySet();

    public IslandPublishRequest(NewSky plugin, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> sendRequest(String operation) {
        String requestID = "Req-" + UUID.randomUUID();

        // Get active servers
        serversToWaitFor.addAll(heartBeatHandler.getActiveServers());
        plugin.debug("Fetched Active Servers: " + serversToWaitFor);

        CompletableFuture<Void> future = new CompletableFuture<>();

        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String responderID = message;
                serversToWaitFor.remove(responderID);
                plugin.debug("Received response from " + responderID + " for request " + requestID + " (" + serversToWaitFor.size() + " remaining)");

                if (serversToWaitFor.isEmpty()) {
                    // Received all responses
                    plugin.debug("Received all responses for request " + requestID);

                    // Unsubscribe from the response channel
                    this.unsubscribe();
                    plugin.debug("Unsubscribed from response channel for request: " + requestID);

                    future.complete(null);

                }
            }
        };

        // Subscribe to the response channel
        redisHandler.subscribe(responseSubscriber, "newsky-response-channel-" + requestID);
        plugin.debug("Subscribed to response channel for request: " + requestID + ", waiting for responses...");

        // Send request
        redisHandler.publish("newsky-request-channel", requestID + ":" + serverID + ":" + operation);
        plugin.debug("Sent request " + requestID + "to request channel.");

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
