package org.me.newsky.messaging;

import org.bukkit.Bukkit;
import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.ClusterKeys;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XReadParams;
import redis.clients.jedis.resps.StreamEntry;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class CrossServerMessenger {

    private static final String FIELD_MESSAGE = "message";
    private static final long REQUEST_TIMEOUT_SECONDS = 30L;
    // A request older than the requester's timeout (plus clock-skew slack) has nobody waiting for
    // it any more; executing it late would replay non-idempotent operations (lock toggles, loads)
    // against state that has long moved on.
    private static final long STALE_REQUEST_MILLIS = (REQUEST_TIMEOUT_SECONDS + 5L) * 1000L;
    // Backstop only: unclogs the inbox window if a handler future never completes. Far above any
    // legitimate operation so it can never masquerade as a real failure verdict.
    private static final long HANDLER_TIMEOUT_SECONDS = 600L;
    // Caps every inbox so streams of dead or renamed servers cannot grow without bound.
    private static final long INBOX_MAX_LEN = 10_000L;
    private static final int READ_BLOCK_MILLIS = 1000;
    private static final int READ_COUNT = 10;

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final String serverID;
    private final Map<String, CrossServerMessageHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<JSONObject>> pendingRequests = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<?>> activeHandlers = ConcurrentHashMap.newKeySet();

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
        sendAsyncIfNeeded(message).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                pendingRequests.remove(message.getMessageId());
                future.completeExceptionally(throwable);
            }
        });

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

    public boolean stop() {
        running = false;
        pendingRequests.forEach((id, future) -> future.completeExceptionally(new IllegalStateException("Cross-server messenger stopped")));
        pendingRequests.clear();
        if (!activeHandlers.isEmpty()) {
            plugin.warning("CrossServerMessenger stopped intake with " + activeHandlers.size() + " request handler(s) still completing");
        }
        return !activeHandlers.isEmpty();
    }

    private void consumeLoop() {
        // XREAD returns entries strictly newer than the supplied ID. Advancing this cursor before
        // dispatch makes the inbox explicitly at-most-once for this boot: an XDEL or response-send
        // failure can cause a caller timeout, but can never replay a committed toggle/mutation.
        // The heartbeat registration atomically clears the previous boot's inbox before this
        // incarnation becomes visible, so a crashed boot's entries are never replayed either.
        // Jedis MINIMUM_ID serializes as "-", which Redis accepts for XRANGE but rejects for
        // XREAD. The explicit 0-0 ID means "all entries newer than the beginning of the stream".
        StreamEntryID lastReadId = new StreamEntryID(0L, 0L);

        while (running && plugin.isEnabled()) {
            try (Jedis jedis = redisHandler.getJedis()) {
                List<Map.Entry<String, List<StreamEntry>>> streams = jedis.xread(XReadParams.xReadParams().block(READ_BLOCK_MILLIS).count(READ_COUNT), Collections.singletonMap(inboxKey(serverID), lastReadId));

                if (streams == null || streams.isEmpty()) {
                    continue;
                }

                for (Map.Entry<String, List<StreamEntry>> stream : streams) {
                    for (StreamEntry entry : stream.getValue()) {
                        lastReadId = entry.getID();
                        processEntry(entry);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    plugin.severe("CrossServerMessenger failed while reading Redis Stream", e);
                    sleepQuietly(1000L);
                }
            }

        }
    }

    private void processEntry(StreamEntry entry) {
        String entryId = entry.getID().toString();

        try {
            String raw = entry.getFields().get(FIELD_MESSAGE);
            if (raw == null || raw.isEmpty()) {
                deleteEntry(entry.getID());
                return;
            }

            CrossServerMessage message = CrossServerMessage.fromJson(raw);
            if (!serverID.equals(message.getTarget())) {
                plugin.warning("Dropping cross-server message targeted to " + message.getTarget() + " from inbox " + serverID);
                deleteEntry(entry.getID());
                return;
            }

            if (CrossServerMessage.TYPE_RESPONSE.equals(message.getType())) {
                handleResponse(message);
                deleteEntry(entry.getID());
                return;
            }

            if (CrossServerMessage.TYPE_REQUEST.equals(message.getType())) {
                if (message.getTimestamp() > 0 && System.currentTimeMillis() - message.getTimestamp() > STALE_REQUEST_MILLIS) {
                    plugin.warning("Dropping stale cross-server request " + message.getAction() + " from " + message.getSource() + " (age exceeds requester timeout)");
                    deleteEntry(entry.getID());
                    return;
                }

                handleRequest(entry.getID(), message);
                return;
            }

            plugin.warning("Dropping unknown cross-server message type: " + message.getType());
            deleteEntry(entry.getID());
        } catch (Exception e) {
            plugin.severe("Failed to process cross-server stream entry " + entryId, e);
            deleteEntry(entry.getID());
        }
    }

    private void handleRequest(StreamEntryID entryId, CrossServerMessage message) {
        CrossServerMessageHandler handler = handlers.get(message.getAction());
        if (handler == null) {
            sendAndDeleteAsync(entryId, CrossServerMessage.failedResponse(message, "No handler registered for action: " + message.getAction()));
            return;
        }

        try {
            CompletableFuture<JSONObject> handling = handler.handle(message.getPayload()).orTimeout(HANDLER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            activeHandlers.add(handling);
            handling.whenCompleteAsync((payload, throwable) -> {
                activeHandlers.remove(handling);
                CrossServerMessage response;
                if (throwable == null) {
                    response = CrossServerMessage.successResponse(message, payload == null ? new JSONObject() : payload);
                } else {
                    response = CrossServerMessage.failedResponse(message, throwable);
                }

                sendAndDelete(entryId, response);
            }, plugin.getBukkitAsyncExecutor());
        } catch (Exception e) {
            sendAndDeleteAsync(entryId, CrossServerMessage.failedResponse(message, e));
        }
    }

    private void handleResponse(CrossServerMessage message) {
        CompletableFuture<JSONObject> future = pendingRequests.remove(message.getCorrelationId());
        if (future == null) {
            plugin.debug("CrossServerMessenger", "No pending request for response " + message.getCorrelationId());
            return;
        }

        CompletableFuture.runAsync(() -> {
            if (CrossServerMessage.STATUS_SUCCESS.equals(message.getStatus())) {
                future.complete(message.getPayload());
            } else {
                future.completeExceptionally(new CompletionException(restoreRemoteException(message)));
            }
        }, plugin.getBukkitAsyncExecutor());
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
        }
    }

    private void sendAndDeleteAsync(StreamEntryID entryId, CrossServerMessage response) {
        CompletableFuture.runAsync(() -> sendAndDelete(entryId, response), plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Void> sendAsyncIfNeeded(CrossServerMessage message) {
        if (Bukkit.isPrimaryThread()) {
            return CompletableFuture.runAsync(() -> send(message), plugin.getBukkitAsyncExecutor());
        }

        try {
            send(message);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private void send(CrossServerMessage message) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.xadd(inboxKey(message.getTarget()), XAddParams.xAddParams().maxLen(INBOX_MAX_LEN).approximateTrimming(), Map.of(FIELD_MESSAGE, message.toJson()));
            plugin.debug("CrossServerMessenger", "Sent " + message.getType() + " " + message.getAction() + " to " + message.getTarget());
        }
    }

    private void deleteEntry(StreamEntryID entryId) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.xdel(inboxKey(serverID), entryId);
        } catch (Exception e) {
            plugin.severe("Failed to delete cross-server stream entry " + entryId, e);
        }
    }

    private String inboxKey(String serverName) {
        return ClusterKeys.messagingInbox(serverName);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
