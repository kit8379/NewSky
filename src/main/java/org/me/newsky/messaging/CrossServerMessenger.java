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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class CrossServerMessenger {

    private static final String FIELD_MESSAGE = "message";
    private static final long REQUEST_TIMEOUT_SECONDS = 30L;
    private static final long STALE_REQUEST_MILLIS = (REQUEST_TIMEOUT_SECONDS + 5L) * 1000L;
    private static final long HANDLER_TIMEOUT_SECONDS = 600L;
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

        future.orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((result, error) -> {
            pendingRequests.remove(message.getMessageId());
        });
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
        pendingRequests.forEach((id, future) -> {
            future.completeExceptionally(new IllegalStateException("Cross-server messenger stopped"));
        });
        pendingRequests.clear();

        if (!activeHandlers.isEmpty()) {
            plugin.warning("CrossServerMessenger stopped intake with " + activeHandlers.size()
                    + " request handler(s) still completing");
        }
        return !activeHandlers.isEmpty();
    }

    private void consumeLoop() {
        // Advance before dispatch: a failed response may time out, but a committed write is never replayed.
        StreamEntryID lastReadId = new StreamEntryID(0L, 0L);

        while (running && plugin.isEnabled()) {
            try (Jedis jedis = redisHandler.getJedis()) {
                XReadParams readParams = XReadParams.xReadParams()
                        .block(READ_BLOCK_MILLIS)
                        .count(READ_COUNT);
                Map<String, StreamEntryID> inbox = Collections.singletonMap(
                        inboxKey(serverID), lastReadId);
                List<Map.Entry<String, List<StreamEntry>>> streams = jedis.xread(readParams, inbox);

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
                plugin.warning("Dropping message targeted to " + message.getTarget()
                        + " from inbox " + serverID);
                deleteEntry(entry.getID());
                return;
            }

            if (CrossServerMessage.TYPE_RESPONSE.equals(message.getType())) {
                handleResponse(message);
                deleteEntry(entry.getID());
                return;
            }

            if (CrossServerMessage.TYPE_REQUEST.equals(message.getType())) {
                if (isStale(message)) {
                    plugin.warning("Dropping stale request " + message.getAction()
                            + " from " + message.getSource());
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

    private boolean isStale(CrossServerMessage message) {
        return message.getTimestamp() > 0
                && System.currentTimeMillis() - message.getTimestamp() > STALE_REQUEST_MILLIS;
    }

    private void handleRequest(StreamEntryID entryId, CrossServerMessage message) {
        CrossServerMessageHandler handler = handlers.get(message.getAction());
        if (handler == null) {
            CrossServerMessage response = CrossServerMessage.failedResponse(message,
                    "No handler registered for action: " + message.getAction());
            sendAndDeleteAsync(entryId, response);
            return;
        }

        try {
            CompletableFuture<JSONObject> handling = handler.handle(message.getPayload())
                    .orTimeout(HANDLER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            activeHandlers.add(handling);
            handling.whenCompleteAsync((payload, throwable) -> {
                activeHandlers.remove(handling);
                CrossServerMessage response = createResponse(message, payload, throwable);
                sendAndDelete(entryId, response);
            }, plugin.getBukkitAsyncExecutor());
        } catch (Exception e) {
            sendAndDeleteAsync(entryId, CrossServerMessage.failedResponse(message, e));
        }
    }

    private CrossServerMessage createResponse(CrossServerMessage request, JSONObject payload,
                                              Throwable error) {
        if (error != null) {
            return CrossServerMessage.failedResponse(request, error);
        }
        return CrossServerMessage.successResponse(request, payload == null ? new JSONObject() : payload);
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

    private RuntimeException instantiateRemoteException(Class<? extends RuntimeException> clazz,
                                                         String errorMessage) throws Exception {
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
            XAddParams addParams = XAddParams.xAddParams()
                    .maxLen(INBOX_MAX_LEN)
                    .approximateTrimming();
            jedis.xadd(inboxKey(message.getTarget()), addParams,
                    Map.of(FIELD_MESSAGE, message.toJson()));
            plugin.debug("CrossServerMessenger", "Sent " + message.getType() + " "
                    + message.getAction() + " to " + message.getTarget());
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
