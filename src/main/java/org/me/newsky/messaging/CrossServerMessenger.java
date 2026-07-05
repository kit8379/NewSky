package org.me.newsky.messaging;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.resps.StreamEntry;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class CrossServerMessenger {

    private static final String STREAM_PREFIX = "newsky:messaging:inbox:";
    private static final String FIELD_MESSAGE = "message";
    private static final long REQUEST_TIMEOUT_SECONDS = 30L;
    private static final int READ_BLOCK_MILLIS = 1000;
    private static final int READ_COUNT = 10;

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final String serverID;
    private final Map<String, CrossServerMessageHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<JSONObject>> pendingRequests = new ConcurrentHashMap<>();
    private final Set<String> processingEntries = ConcurrentHashMap.newKeySet();

    private volatile boolean running;

    public CrossServerMessenger(NewSky plugin, RedisHandler redisHandler, String serverID) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.redisHandler = Objects.requireNonNull(redisHandler, "redisHandler");
        this.serverID = Objects.requireNonNull(serverID, "serverID");
    }

    public void register(String action, CrossServerMessageHandler handler) {
        handlers.put(Objects.requireNonNull(action, "action"), Objects.requireNonNull(handler, "handler"));
    }

    public CompletableFuture<JSONObject> request(String targetServer, String action, JSONObject payload) {
        CrossServerMessage message = CrossServerMessage.request(serverID, targetServer, action, payload);
        CompletableFuture<JSONObject> future = new CompletableFuture<>();

        pendingRequests.put(message.getMessageId(), future);
        try {
            send(message);
        } catch (Exception e) {
            pendingRequests.remove(message.getMessageId());
            future.completeExceptionally(e);
            return future;
        }

        future.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((result, throwable) -> pendingRequests.remove(message.getMessageId()));
        return future;
    }

    public CompletableFuture<Void> requestVoid(String targetServer, String action, JSONObject payload) {
        return request(targetServer, action, payload).thenApply(result -> null);
    }

    public void start() {
        if (running) {
            return;
        }

        running = true;
        CompletableFuture.runAsync(this::consumeLoop, plugin.getBukkitAsyncExecutor());
        plugin.debug("CrossServerMessenger", "Started Redis Stream consumer for " + serverID);
    }

    public void stop() {
        running = false;
        pendingRequests.forEach((id, future) -> future.completeExceptionally(new IllegalStateException("Cross-server messenger stopped")));
        pendingRequests.clear();
    }

    private void consumeLoop() {
        while (running && plugin.isEnabled()) {
            boolean skippedOnly = true;

            try (Jedis jedis = redisHandler.getJedis()) {
                List<Map.Entry<String, List<StreamEntry>>> streams = jedis.xread(XReadParams.xReadParams().block(READ_BLOCK_MILLIS).count(READ_COUNT), Collections.singletonMap(inboxKey(serverID), StreamEntryID.MINIMUM_ID));

                if (streams == null || streams.isEmpty()) {
                    continue;
                }

                for (Map.Entry<String, List<StreamEntry>> stream : streams) {
                    for (StreamEntry entry : stream.getValue()) {
                        if (processEntry(entry)) {
                            skippedOnly = false;
                        }
                    }
                }
            } catch (Exception e) {
                if (running) {
                    plugin.severe("CrossServerMessenger failed while reading Redis Stream", e);
                    sleepQuietly(1000L);
                }
            }

            if (skippedOnly) {
                sleepQuietly(50L);
            }
        }
    }

    private boolean processEntry(StreamEntry entry) {
        String entryId = entry.getID().toString();
        if (!processingEntries.add(entryId)) {
            return false;
        }

        try {
            String raw = entry.getFields().get(FIELD_MESSAGE);
            if (raw == null || raw.isEmpty()) {
                deleteEntry(entry.getID());
                return true;
            }

            CrossServerMessage message = CrossServerMessage.fromJson(raw);
            if (!serverID.equals(message.getTarget())) {
                plugin.warning("Dropping cross-server message targeted to " + message.getTarget() + " from inbox " + serverID);
                deleteEntry(entry.getID());
                return true;
            }

            if (CrossServerMessage.TYPE_RESPONSE.equals(message.getType())) {
                handleResponse(message);
                deleteEntry(entry.getID());
                return true;
            }

            if (CrossServerMessage.TYPE_REQUEST.equals(message.getType())) {
                handleRequest(entry.getID(), message);
                return true;
            }

            plugin.warning("Dropping unknown cross-server message type: " + message.getType());
            deleteEntry(entry.getID());
            return true;
        } catch (Exception e) {
            plugin.severe("Failed to process cross-server stream entry " + entryId, e);
            deleteEntry(entry.getID());
            return true;
        }
    }

    private void handleRequest(StreamEntryID entryId, CrossServerMessage message) {
        CrossServerMessageHandler handler = handlers.get(message.getAction());
        if (handler == null) {
            sendAndDelete(entryId, CrossServerMessage.failedResponse(message, "No handler registered for action: " + message.getAction()));
            return;
        }

        try {
            handler.handle(message.getPayload()).whenComplete((payload, throwable) -> {
                CrossServerMessage response;
                if (throwable == null) {
                    response = CrossServerMessage.successResponse(message, payload == null ? new JSONObject() : payload);
                } else {
                    response = CrossServerMessage.failedResponse(message, throwable);
                }

                sendAndDelete(entryId, response);
            });
        } catch (Exception e) {
            sendAndDelete(entryId, CrossServerMessage.failedResponse(message, e));
        }
    }

    private void handleResponse(CrossServerMessage message) {
        CompletableFuture<JSONObject> future = pendingRequests.remove(message.getCorrelationId());
        if (future == null) {
            plugin.debug("CrossServerMessenger", "No pending request for response " + message.getCorrelationId());
            return;
        }

        if (CrossServerMessage.STATUS_SUCCESS.equals(message.getStatus())) {
            future.complete(message.getPayload());
        } else {
            future.completeExceptionally(new CompletionException(restoreRemoteException(message)));
        }
    }

    private Throwable restoreRemoteException(CrossServerMessage message) {
        String errorType = message.getErrorType();
        String errorMessage = message.getErrorMessage() == null ? "Remote request failed" : message.getErrorMessage();

        if (errorType == null || errorType.isEmpty()) {
            return new IllegalStateException(errorMessage);
        }

        try {
            Class<?> clazz = Class.forName(errorType);
            if (!RuntimeException.class.isAssignableFrom(clazz)) {
                return new IllegalStateException(errorMessage);
            }

            return instantiateRemoteException(clazz.asSubclass(RuntimeException.class), errorMessage);
        } catch (Exception e) {
            plugin.severe("Failed to restore remote exception type: " + errorType, e);
            return new IllegalStateException(errorMessage);
        }
    }

    private RuntimeException instantiateRemoteException(Class<? extends RuntimeException> clazz, String errorMessage) throws Exception {
        try {
            Constructor<? extends RuntimeException> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException ignored) {
            Constructor<? extends RuntimeException> constructor = clazz.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(errorMessage);
        }
    }

    private void sendAndDelete(StreamEntryID entryId, CrossServerMessage response) {
        try {
            send(response);
        } catch (Exception e) {
            plugin.severe("Failed to send cross-server response for " + response.getCorrelationId(), e);
        } finally {
            deleteEntry(entryId);
            processingEntries.remove(entryId.toString());
        }
    }

    private void send(CrossServerMessage message) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.xadd(inboxKey(message.getTarget()), StreamEntryID.NEW_ENTRY, Map.of(FIELD_MESSAGE, message.toJson()));
            plugin.debug("CrossServerMessenger", "Sent " + message.getType() + " " + message.getAction() + " to " + message.getTarget());
        }
    }

    private void deleteEntry(StreamEntryID entryId) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.xdel(inboxKey(serverID), entryId);
        } catch (Exception e) {
            plugin.severe("Failed to delete cross-server stream entry " + entryId, e);
        } finally {
            processingEntries.remove(entryId.toString());
        }
    }

    private String inboxKey(String serverName) {
        return STREAM_PREFIX + serverName;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
