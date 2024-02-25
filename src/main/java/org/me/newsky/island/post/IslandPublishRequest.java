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
                String responseStatus = responseParts[1];
                String responseData = responseParts.length > 2 ? responseParts[2] : "";

                responses.put(responderID, responseData);
                plugin.debug("Received response from server: " + responderID + " for request: " + requestID + " for operation: " + operation + " with status: " + responseStatus + " and data: " + responseData);

                boolean shouldComplete = (targetServer.equals("all") && responses.keySet().containsAll(activeServers)) || responderID.equals(targetServer);

                if (shouldComplete) {
                    this.unsubscribe();
                    plugin.debug("All responses received for request: " + requestID + ", unsubscribed from response channel.");

                    // If all responses are "Success", complete the future with the responses
                    if (responses.values().stream().allMatch(s -> s.equals("Success"))) {
                        future.complete(responses);
                    } else {
                        // Find the first error response message
                        String errorMessage = responses.values().stream().filter(s -> s.equals("Error")).findFirst().orElse("Unknown error");
                        future.completeExceptionally(new IllegalStateException(errorMessage));
                    }
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "newsky-response-channel-" + requestID);
        String requestMessage = requestID + ":" + serverID + ":" + targetServer + ":" + operation;

        redisHandler.publish("newsky-request-channel", requestMessage);
        plugin.debug("Sending request: " + requestID + " to server: " + targetServer + " for operation: " + operation);

        return future;
    }
}