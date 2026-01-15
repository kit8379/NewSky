package org.me.newsky.broker;

import org.json.JSONArray;
import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.network.operator.Operator;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.util.ComponentUtils;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Broker {

    private static final ConcurrentHashMap<String, CompletableFuture<Void>> pendingRequests = new ConcurrentHashMap<>();

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final Operator operator;
    private final String serverID;
    private final String channelID;
    private JedisPubSub subscriber;

    public Broker(NewSky plugin, RedisHandler redisHandler, Operator operator, String serverID, String channelID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.operator = operator;
        this.serverID = serverID;
        this.channelID = channelID;
    }

    public void subscribe() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                plugin.debug("Broker", "Received message on channel " + channel + ": " + message);
                try {
                    JSONObject json = new JSONObject(message);
                    String type = json.getString("type");

                    if ("request".equals(type)) {
                        handleRequest(json);
                    } else if ("response".equals(type)) {
                        handleResponse(json);
                    }
                } catch (Exception e) {
                    plugin.severe("Error processing message: " + message, e);
                }
            }
        };

        redisHandler.subscribe(subscriber, channelID);
        plugin.debug("Broker", "Subscribed to channel " + channelID);
    }

    public void unsubscribe() {
        if (subscriber != null) {
            subscriber.unsubscribe();
            plugin.debug("Broker", "Unsubscribed from channel " + channelID);
        }
    }

    public CompletableFuture<Void> sendRequest(String targetServer, String operation, String... args) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, future);

        JSONObject json = new JSONObject();
        json.put("type", "request");
        json.put("requestId", requestId);
        json.put("source", serverID);
        json.put("target", targetServer);
        json.put("operator", operation);
        json.put("args", args);

        plugin.debug("Broker", "Sending request " + operation + " to server " + targetServer + " with ID " + requestId);
        redisHandler.publish(channelID, json.toString());

        future.orTimeout(30, TimeUnit.SECONDS).exceptionally(error -> {
            pendingRequests.remove(requestId);
            plugin.severe("Request " + operation + " to server " + targetServer + " timed out.", error);
            return null;
        });

        return future;
    }

    private void sendResponse(String status, String requestId, String destination) {
        JSONObject json = new JSONObject();
        json.put("type", "response");
        json.put("status", status);
        json.put("requestId", requestId);
        json.put("source", serverID);
        json.put("target", destination);

        plugin.debug("Broker", "Sending response for request " + requestId + " with status " + status + " to server " + destination);
        redisHandler.publish(channelID, json.toString());
    }

    private void handleRequest(JSONObject json) {
        String requestId = json.getString("requestId");
        String source = json.getString("source");
        String target = json.getString("target");
        String operation = json.getString("operator");
        plugin.debug("Broker", "Received request " + operation + " from server " + source + " with ID " + requestId);

        if (!serverID.equals(target)) {
            plugin.debug("Broker", "Ignoring request because target server " + target + " does not match this server " + serverID);
            return;
        }

        JSONArray jsonArgs = json.getJSONArray("args");
        String[] args = new String[jsonArgs.length()];
        for (int i = 0; i < jsonArgs.length(); i++) {
            args[i] = jsonArgs.getString(i);
        }

        processRequest(operation, args).thenRun(() -> sendResponse("success", requestId, source)).exceptionally(e -> {
            sendResponse("fail", requestId, source);
            plugin.severe("Failed to process request " + operation + " from server " + source + " with ID " + requestId, e);
            return null;
        });
    }

    private void handleResponse(JSONObject json) {
        String status = json.getString("status");
        String requestId = json.getString("requestId");
        String source = json.getString("source");
        String target = json.getString("target");
        plugin.debug("Broker", "Received response for request " + requestId + " from " + source + " with status " + status);

        if (!serverID.equals(target)) {
            plugin.debug("Broker", "Ignoring response because target server " + target + " does not match this server " + serverID);
            return;
        }

        CompletableFuture<Void> future = pendingRequests.remove(requestId);

        if (future == null) {
            return;
        }

        if ("success".equals(status)) {
            future.complete(null);
        } else {
            future.completeExceptionally(new IllegalStateException("Request failed: " + requestId));
        }
    }

    private CompletableFuture<Void> processRequest(String operation, String... args) {
        try {
            switch (operation) {
                case "create":
                    return operator.createIsland(UUID.fromString(args[0]));
                case "delete":
                    return operator.deleteIsland(UUID.fromString(args[0]));
                case "load":
                    return operator.loadIsland(UUID.fromString(args[0]));
                case "unload":
                    return operator.unloadIsland(UUID.fromString(args[0]));
                case "teleport":
                    return operator.teleport(UUID.fromString(args[0]), args[1], args[2]);
                case "lock":
                    return operator.lockIsland(UUID.fromString(args[0]));
                case "expel":
                    return operator.expelPlayer(UUID.fromString(args[0]), UUID.fromString(args[1]));
                case "message":
                    return operator.sendMessage(UUID.fromString(args[0]), ComponentUtils.deserialize(args[1]));
                default:
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown request operator: " + operation));
            }
        } catch (Exception e) {
            plugin.severe("Error processing request operator " + operation + " with args: " + String.join(", ", args), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
