package org.me.newsky.redis;

import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class RedisPublishRequest {

    private final RedisHandler redisHandler;
    private final RedisHeartBeat redisHeartBeat;
    private final String serverID;
    private final Set<String> respondedServers = ConcurrentHashMap.newKeySet();
    private final Set<String> serversToWaitFor = ConcurrentHashMap.newKeySet();

    public RedisPublishRequest(RedisHandler redisHandler, RedisHeartBeat redisHeartBeat, String serverID) {
        this.redisHandler = redisHandler;
        this.redisHeartBeat = redisHeartBeat;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> sendRequest(String operation) {
        String requestID = "Req-" + UUID.randomUUID();

        // Add all active servers to the list of servers to wait for
        serversToWaitFor.addAll(redisHeartBeat.getActiveServers());

        // Send the request
        redisHandler.publish("newsky-request-channel", serverID + ":" + requestID + ":" + operation);

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Wait for response
        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String responderID = parts[0];
                String receivedRequestID = parts[1];

                if (receivedRequestID.equals(requestID)) {
                    respondedServers.add(responderID);
                    serversToWaitFor.remove(responderID);

                    if (serversToWaitFor.isEmpty()) {
                        this.unsubscribe();
                        future.complete(null);
                    }
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "newsky-response-channel-" + serverID);

        // Timeout handling
        CompletableFuture.runAsync(() -> {
            try {
                // Wait for a certain time for responses
                future.get(10000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                // Here you can handle/log servers that did not respond if needed
                serversToWaitFor.forEach(serversToWaitFor::remove);
                responseSubscriber.unsubscribe();
                future.complete(null);
            }
        });

        return future;
    }
}
