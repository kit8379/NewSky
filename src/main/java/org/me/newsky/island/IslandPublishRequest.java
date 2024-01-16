package org.me.newsky.island;

import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisHeartBeat;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class IslandPublishRequest {

    private final RedisHandler redisHandler;
    private final RedisHeartBeat redisHeartBeat;
    private final String serverID;
    private final Set<String> serversToWaitFor = ConcurrentHashMap.newKeySet();

    public IslandPublishRequest(RedisHandler redisHandler, RedisHeartBeat redisHeartBeat, String serverID) {
        this.redisHandler = redisHandler;
        this.redisHeartBeat = redisHeartBeat;
        this.serverID = serverID;
    }


    public CompletableFuture<Void> sendRequest(String operation) {
        String requestID = "Req-" + UUID.randomUUID();

        // Add all active servers to the list of servers to wait for
        serversToWaitFor.addAll(redisHeartBeat.getActiveServers());

        // Send the request
        redisHandler.publish("newsky-request-channel", requestID + ":" + serverID + ":" + operation);

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Wait for response
        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String responderID = message;

                serversToWaitFor.remove(responderID);

                if (serversToWaitFor.isEmpty()) {
                    this.unsubscribe();
                    future.complete(null);
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "newsky-response-channel-" + requestID);

        // Schedule a task to complete the future exceptionally after a timeout
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("Timeout waiting for servers to respond"));
            }
            executor.shutdown();
        }, 30, TimeUnit.SECONDS); // 30 seconds timeout

        return future.exceptionally(throwable -> {
            // Clean up in case of any exception
            responseSubscriber.unsubscribe();
            return null;
        });
    }
}
