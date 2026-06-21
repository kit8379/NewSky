package org.me.newsky.broker;

import org.json.JSONArray;
import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.network.IslandOperator;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class IslandBroker {

    private static final String KEY_TYPE = "type";
    private static final String KEY_REQUEST_ID = "requestId";
    private static final String KEY_STATUS = "status";
    private static final String KEY_SOURCE = "source";
    private static final String KEY_TARGET = "target";
    private static final String KEY_OPERATION = "operation";
    private static final String KEY_ARGS = "args";

    private static final String TYPE_REQUEST = "request";
    private static final String TYPE_RESPONSE = "response";
    private static final String TYPE_EVENT = "event";

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAIL = "fail";

    private static final String OP_CREATE = "create";
    private static final String OP_DELETE = "delete";
    private static final String OP_LOAD = "load";
    private static final String OP_UNLOAD = "unload";
    private static final String OP_TELEPORT = "teleport";
    private static final String OP_LOCK = "lock";
    private static final String OP_EXPEL = "expel";
    private static final String OP_UPDATE_BORDER = "update_border";
    private static final String OP_RELOAD_SNAPSHOT = "reload_snapshot";

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final IslandOperator islandOperator;
    private final String serverID;
    private final String channelID;

    private final ConcurrentHashMap<String, CompletableFuture<Void>> pendingRequests = new ConcurrentHashMap<>();

    private JedisPubSub subscriber;

    public IslandBroker(NewSky plugin, RedisHandler redisHandler, IslandOperator islandOperator, String serverID, String channelID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.islandOperator = islandOperator;
        this.serverID = serverID;
        this.channelID = channelID;
    }

    public void subscribe() {
        subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                plugin.debug("IslandBroker", "Received message on channel " + channel + ": " + message);
                try {
                    JSONObject json = new JSONObject(message);
                    String type = json.getString(KEY_TYPE);

                    switch (type) {
                        case TYPE_REQUEST:
                            handleRequest(json);
                            break;
                        case TYPE_RESPONSE:
                            handleResponse(json);
                            break;
                        case TYPE_EVENT:
                            handleEvent(json);
                            break;
                        default:
                            plugin.debug("IslandBroker", "Ignoring unknown message type: " + type);
                            break;
                    }
                } catch (Exception e) {
                    plugin.severe("Error processing message: " + message, e);
                }
            }
        };

        redisHandler.subscribe(subscriber, channelID);
        plugin.debug("IslandBroker", "Subscribed to channel " + channelID);
    }

    public void unsubscribe() {
        if (subscriber != null) {
            subscriber.unsubscribe();
            plugin.debug("IslandBroker", "Unsubscribed from channel " + channelID);
        }
    }

    public CompletableFuture<Void> sendRequest(String targetServer, String operation, String... args) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String requestId = UUID.randomUUID().toString();

        JSONObject json = new JSONObject();
        json.put(KEY_TYPE, TYPE_REQUEST);
        json.put(KEY_REQUEST_ID, requestId);
        json.put(KEY_SOURCE, serverID);
        json.put(KEY_TARGET, targetServer);
        json.put(KEY_OPERATION, operation);
        json.put(KEY_ARGS, args);

        pendingRequests.put(requestId, future);

        try {
            plugin.debug("IslandBroker", "Sending request " + operation + " to server " + targetServer + " with ID " + requestId);
            redisHandler.publish(channelID, json.toString());
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            plugin.severe("Failed to publish request " + operation + " to server " + targetServer + " with ID " + requestId, e);
            future.completeExceptionally(e);
            return future;
        }

        future.orTimeout(60, TimeUnit.SECONDS);

        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                pendingRequests.remove(requestId);
                plugin.severe("Request " + operation + " to server " + targetServer + " failed or timed out. Request ID: " + requestId, throwable);
            }
        });

        return future;
    }

    private void sendResponse(String status, String requestId, String destination) {
        JSONObject json = new JSONObject();
        json.put(KEY_TYPE, TYPE_RESPONSE);
        json.put(KEY_STATUS, status);
        json.put(KEY_REQUEST_ID, requestId);
        json.put(KEY_SOURCE, serverID);
        json.put(KEY_TARGET, destination);

        try {
            plugin.debug("IslandBroker", "Sending response for request " + requestId + " with status " + status + " to server " + destination);
            redisHandler.publish(channelID, json.toString());
        } catch (Exception e) {
            plugin.severe("Failed to publish response for request " + requestId + " to server " + destination, e);
        }
    }

    public void sendEvent(String targetServer, String operation, String... args) {
        JSONObject json = new JSONObject();
        json.put(KEY_TYPE, TYPE_EVENT);
        json.put(KEY_SOURCE, serverID);
        json.put(KEY_TARGET, targetServer);
        json.put(KEY_OPERATION, operation);
        json.put(KEY_ARGS, args);

        try {
            plugin.debug("IslandBroker", "Sending event " + operation + " to server " + targetServer);
            redisHandler.publish(channelID, json.toString());
        } catch (Exception e) {
            plugin.severe("Failed to publish event " + operation + " to server " + targetServer, e);
        }
    }

    private void handleRequest(JSONObject json) {
        String requestId = json.getString(KEY_REQUEST_ID);
        String source = json.getString(KEY_SOURCE);
        String target = json.getString(KEY_TARGET);
        String operation = json.getString(KEY_OPERATION);

        plugin.debug("IslandBroker", "Received request " + operation + " from server " + source + " with ID " + requestId);

        if (!serverID.equals(target)) {
            plugin.debug("IslandBroker", "Ignoring request because target server " + target + " does not match this server " + serverID);
            return;
        }

        String[] args = readArgs(json.getJSONArray(KEY_ARGS));

        processRequest(operation, args).thenRun(() -> sendResponse(STATUS_SUCCESS, requestId, source)).exceptionally(e -> {
            sendResponse(STATUS_FAIL, requestId, source);
            plugin.severe("Failed to process request " + operation + " from server " + source + " with ID " + requestId, e);
            return null;
        });
    }

    private void handleResponse(JSONObject json) {
        String status = json.getString(KEY_STATUS);
        String requestId = json.getString(KEY_REQUEST_ID);
        String source = json.getString(KEY_SOURCE);
        String target = json.getString(KEY_TARGET);

        plugin.debug("IslandBroker", "Received response for request " + requestId + " from " + source + " with status " + status);

        if (!serverID.equals(target)) {
            plugin.debug("IslandBroker", "Ignoring response because target server " + target + " does not match this server " + serverID);
            return;
        }

        CompletableFuture<Void> future = pendingRequests.remove(requestId);
        if (future == null) {
            plugin.debug("IslandBroker", "No pending request found for response ID " + requestId);
            return;
        }

        if (STATUS_SUCCESS.equals(status)) {
            future.complete(null);
        } else {
            future.completeExceptionally(new IllegalStateException("Request failed: " + requestId));
        }
    }

    private void handleEvent(JSONObject json) {
        String source = json.getString(KEY_SOURCE);
        String target = json.getString(KEY_TARGET);
        String operation = json.getString(KEY_OPERATION);

        plugin.debug("IslandBroker", "Received event " + operation + " from server " + source);

        if (!serverID.equals(target)) {
            plugin.debug("IslandBroker", "Ignoring event because target server " + target + " does not match this server " + serverID);
            return;
        }

        String[] args = readArgs(json.getJSONArray(KEY_ARGS));

        processEvent(operation, args);
    }

    private CompletableFuture<Void> processRequest(String operation, String... args) {
        try {
            switch (operation) {
                case OP_CREATE:
                    return islandOperator.createIsland(UUID.fromString(args[0]));
                case OP_DELETE:
                    return islandOperator.deleteIsland(UUID.fromString(args[0]));
                case OP_LOAD:
                    return islandOperator.loadIsland(UUID.fromString(args[0]));
                case OP_UNLOAD:
                    return islandOperator.unloadIsland(UUID.fromString(args[0]));
                case OP_TELEPORT:
                    return islandOperator.teleport(UUID.fromString(args[0]), args[1], args[2]);
                default:
                    return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown request island operation: " + operation));
            }
        } catch (Exception e) {
            plugin.severe("Error processing request island operation " + operation + " with args: " + String.join(", ", args), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private void processEvent(String operation, String... args) {
        try {
            switch (operation) {
                case OP_LOCK:
                    islandOperator.lockIsland(UUID.fromString(args[0]));
                    break;
                case OP_EXPEL:
                    islandOperator.expelPlayer(UUID.fromString(args[0]), UUID.fromString(args[1]));
                    break;
                case OP_UPDATE_BORDER:
                    islandOperator.updateBorder(UUID.fromString(args[0]), Integer.parseInt(args[1]));
                    break;
                case OP_RELOAD_SNAPSHOT:
                    islandOperator.reloadSnapshot(UUID.fromString(args[0]));
                    break;
                default:
                    plugin.severe("Unknown event island operation: " + operation, null);
                    break;
            }
        } catch (Exception e) {
            plugin.severe("Error processing event island operation " + operation + " with args: " + String.join(", ", args), e);
        }
    }

    private String[] readArgs(JSONArray jsonArgs) {
        String[] args = new String[jsonArgs.length()];
        for (int i = 0; i < jsonArgs.length(); i++) {
            args[i] = jsonArgs.getString(i);
        }
        return args;
    }
}