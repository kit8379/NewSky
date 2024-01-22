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

public class UpdatePublishRequest {

    private static final long TIMEOUT_SECONDS = 30L;
    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final HeartBeatHandler heartBeatHandler;
    private final String serverID;
    private final Set<String> serversToWaitFor = ConcurrentHashMap.newKeySet();

    public UpdatePublishRequest(NewSky plugin, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<Set<String>> sendUpdateRequest() {
        String requestID = "UpdateReq-" + UUID.randomUUID();

        serversToWaitFor.addAll(heartBeatHandler.getActiveServers());
        plugin.debug("Fetched Active Servers: " + serversToWaitFor);

        CompletableFuture<Set<String>> future = new CompletableFuture<>();
        Set<String> responses = ConcurrentHashMap.newKeySet();

        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                plugin.debug("Raw response message: " + message);

                String[] parts = message.split(":");
                String responderID = parts[0];
                String responseData = parts.length > 1 ? parts[1] : null;

                serversToWaitFor.remove(responderID);
                if (responseData != null) {
                    responses.add(responderID + ":" + responseData);
                }
                plugin.debug("Received update response from " + responderID + " for request " + requestID + " (" + serversToWaitFor.size() + " remaining)");

                if (serversToWaitFor.isEmpty()) {
                    this.unsubscribe();
                    plugin.debug("Unsubscribed from update response channel for request: " + requestID);
                    future.complete(responses);
                }
            }
        };

        // Subscribe to the response channel
        redisHandler.subscribe(responseSubscriber, "newsky-update-response-channel-" + requestID);
        plugin.debug("Subscribed to update response channel for request: " + requestID + ", waiting for update responses...");

        // Publish the request
        redisHandler.publish("newsky-update-request-channel", requestID + ":" + serverID + ":updateWorldList");
        plugin.debug("Sent update request " + requestID + " to update request channel.");

        scheduleTimeoutTask(future, requestID, responseSubscriber);

        return future;
    }

    private void scheduleTimeoutTask(CompletableFuture<Set<String>> future, String requestID, JedisPubSub responseSubscriber) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!future.isDone()) {
                plugin.info("Update request " + requestID + " timed out.");
                responseSubscriber.unsubscribe();
                plugin.debug("Unsubscribed from update response channel due to timeout for request: " + requestID);
                future.completeExceptionally(new TimeoutException("Timeout waiting for update responses to request: " + requestID));
            }
        }, TIMEOUT_SECONDS * 20);
    }
}
