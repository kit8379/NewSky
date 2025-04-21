package org.me.newsky.network;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

public class RedisPublishRequest {

    protected static final ConcurrentHashMap<String, CompletableFuture<Void>> pendingRequests = new ConcurrentHashMap<>();
    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final String serverID;
    private JedisPubSub responseSubscriber;

    public RedisPublishRequest(NewSky plugin, RedisHandler redisHandler, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
    }

    public void subscribeToResponseChannel() {
        responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String messageType = parts[0];
                String messageStatus = parts[1];
                String requestID = parts[2];
                String sourceServer = parts[3];
                String targetServer = parts[4];

                if (messageType.equals("response") && targetServer.equals(serverID)) {
                    if (messageStatus.equals("success")) {
                        plugin.debug("Received success response for request ID " + requestID + " from " + sourceServer);
                        CompletableFuture<Void> future = pendingRequests.get(requestID);
                        if (future != null) {
                            future.complete(null);
                            pendingRequests.remove(requestID);
                            plugin.debug("Completed request with request ID " + requestID);
                        }
                    } else {
                        plugin.debug("Received failure response for request ID " + requestID + " from " + sourceServer);
                        CompletableFuture<Void> future = pendingRequests.get(requestID);
                        if (future != null) {
                            future.completeExceptionally(new RuntimeException());
                            pendingRequests.remove(requestID);
                            plugin.debug("Completed request with request ID " + requestID);
                        }
                    }
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "newsky-response-channel");
    }

    public void unsubscribeFromResponseChannel() {
        responseSubscriber.unsubscribe();
    }

    public CompletableFuture<Void> sendRequest(String targetServer, String operation, String... args) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String requestID = UUID.randomUUID().toString();
        pendingRequests.put(requestID, future);

        StringBuilder requestMessage = new StringBuilder("request:" + requestID + ":" + serverID + ":" + targetServer + ":" + operation);
        for (String arg : args) {
            requestMessage.append(":").append(arg);
        }

        redisHandler.publish("newsky-request-channel", requestMessage.toString());
        plugin.debug("Sent request to " + targetServer + " for " + operation + " with request ID " + requestID);

        // Set a future timeout of 30 seconds
        future.orTimeout(30, TimeUnit.SECONDS).whenComplete((result, error) -> {
            if (error instanceof TimeoutException) {
                pendingRequests.remove(requestID);
                future.completeExceptionally(new TimeoutException("Request timed out"));
                plugin.getLogger().log(Level.SEVERE, "Request with ID " + requestID + " timed out after 30 seconds");
            }
        });

        return future;
    }
}
