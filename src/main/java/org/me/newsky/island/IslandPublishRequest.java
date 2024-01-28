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

    public IslandPublishRequest(NewSky plugin, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<ConcurrentHashMap<String, String>> sendRequest(String targetServer, String operation) {
        String requestID = "Req-" + UUID.randomUUID();
        CompletableFuture<ConcurrentHashMap<String, String>> future = new CompletableFuture<>();
        ConcurrentHashMap<String, String> responses = new ConcurrentHashMap<>();
        Set<String> activeServers = heartBeatHandler.getActiveServers();

        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] responseParts = message.split(":");
                String responderID = responseParts[0];
                String responseData = responseParts.length > 1 ? responseParts[1] : "";

                responses.put(responderID, responseData);
                plugin.debug("Received response from server: " + responderID + " for request: " + requestID + " for operation: " + operation + " with data: " + responseData);

                if (targetServer.equals("all") && responses.keySet().containsAll(activeServers)) {
                    this.unsubscribe();
                    future.complete(responses);
                } else if (responderID.equals(targetServer)) {
                    this.unsubscribe();
                    future.complete(responses);
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "newsky-response-channel-" + requestID);
        String requestMessage = requestID + ":" + serverID + ":" + targetServer + ":" + operation;

        redisHandler.publish("newsky-request-channel", requestMessage);
        plugin.debug("Sending request: " + requestID + " to server: " + targetServer + " for operation: " + operation);

        scheduleTimeoutTask(future, requestID, responseSubscriber);

        return future;
    }

    private void scheduleTimeoutTask(CompletableFuture<ConcurrentHashMap<String, String>> future, String requestID, JedisPubSub responseSubscriber) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!future.isDone()) {
                responseSubscriber.unsubscribe();
                future.completeExceptionally(new TimeoutException("Timeout waiting for response to request: " + requestID));
            }
        }, TIMEOUT_SECONDS * 20);
    }
}
