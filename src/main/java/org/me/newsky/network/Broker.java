package org.me.newsky.network;

import org.me.newsky.NewSky;
import org.me.newsky.island.middleware.LocalIslandOperation;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

public class Broker {

    private static final String CHANNEL = "newsky-channel";
    private static final ConcurrentHashMap<String, CompletableFuture<Void>> pendingRequests = new ConcurrentHashMap<>();

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final LocalIslandOperation localIslandOperation;
    private final String serverID;
    private JedisPubSub subscriber;

    public Broker(NewSky plugin, RedisHandler redisHandler, LocalIslandOperation localIslandOperation, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.localIslandOperation = localIslandOperation;
        this.serverID = serverID;
    }

    public void subscribe() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                if (parts.length < 5) return;

                String type = parts[0];
                if (type.equals("request")) {
                    handleRequest(parts);
                } else if (type.equals("response")) {
                    handleResponse(parts);
                }
            }
        };

        redisHandler.subscribe(subscriber, CHANNEL);
    }

    public void unsubscribe() {
        if (subscriber != null) {
            subscriber.unsubscribe();
        }
    }

    public CompletableFuture<Void> sendRequest(String targetServer, String operation, String... args) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, future);

        StringBuilder message = new StringBuilder("request:" + requestId + ":" + serverID + ":" + targetServer + ":" + operation);
        for (String arg : args) {
            message.append(":").append(arg);
        }

        redisHandler.publish(CHANNEL, message.toString());
        plugin.debug("Sent request " + operation + " to " + targetServer + " [ID=" + requestId + "]");

        future.orTimeout(30, TimeUnit.SECONDS).exceptionally(error -> {
            pendingRequests.remove(requestId);
            plugin.getLogger().log(Level.SEVERE, "Request timeout for ID " + requestId, error);
            return null;
        });

        return future;
    }

    private void handleRequest(String[] parts) {
        String requestId = parts[1];
        String source = parts[2];
        String target = parts[3];
        String operation = parts[4];

        if (!target.equals(serverID)) return;

        String[] args = new String[parts.length - 5];
        System.arraycopy(parts, 5, args, 0, args.length);

        plugin.debug("Received request " + operation + " from " + source + " [ID=" + requestId + "]");

        processRequest(operation, args).thenRun(() -> {
            sendResponse("success", requestId, source);
        }).exceptionally(e -> {
            sendResponse("fail", requestId, source);
            plugin.getLogger().log(Level.SEVERE, "Failed to handle request " + operation, e);
            return null;
        });
    }

    private void sendResponse(String status, String requestId, String destination) {
        String msg = String.join(":", "response", status, requestId, serverID, destination);
        redisHandler.publish(CHANNEL, msg);
    }

    private void handleResponse(String[] parts) {
        String status = parts[1];
        String requestId = parts[2];
        String source = parts[3];
        String target = parts[4];

        if (!target.equals(serverID)) {
            return;
        }

        CompletableFuture<Void> future = pendingRequests.remove(requestId);

        if (future == null) {
            return;
        }

        plugin.debug("Response [" + status + "] for request ID " + requestId + " from " + source);
        if (status.equals("success")) {
            future.complete(null);
        } else {
            future.completeExceptionally(new RuntimeException("Request failed: " + requestId));
        }
    }


    private CompletableFuture<Void> processRequest(String operation, String... args) {
        try {
            switch (operation) {
                case "create":
                    return localIslandOperation.createIsland(UUID.fromString(args[0]));
                case "delete":
                    return localIslandOperation.deleteIsland(UUID.fromString(args[0]));
                case "load":
                    return localIslandOperation.loadIsland(UUID.fromString(args[0]));
                case "unload":
                    return localIslandOperation.unloadIsland(UUID.fromString(args[0]));
                case "lock":
                    return localIslandOperation.lockIsland(UUID.fromString(args[0]));
                case "expel":
                    return localIslandOperation.expelPlayer(UUID.fromString(args[0]), UUID.fromString(args[1]));
                case "teleport":
                    return localIslandOperation.teleportToIsland(UUID.fromString(args[0]), UUID.fromString(args[1]), args[2]);
                default:
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown operation: " + operation));
            }
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
