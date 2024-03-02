package org.me.newsky.island.post;

import org.me.newsky.NewSky;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class IslandPublishRequest {
    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final HeartBeatHandler heartBeatHandler;
    private final String serverID;
    private static final ConcurrentHashMap<String, RequestData> pendingRequests = new ConcurrentHashMap<>();

    public IslandPublishRequest(NewSky plugin, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler,
                                String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.serverID = serverID;
        subscribeToResponseChannel();
    }

    private void subscribeToResponseChannel() {
        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String messageType = parts[0];
                String requestID = parts[1];
                String responseServerID = parts[2];
                String response = parts.length > 3 ? parts[3] : "";

                if (messageType.equals("response")) {
                    RequestData requestData = pendingRequests.get(requestID);
                    if (requestData != null) {
                        requestData.getResponses().put(responseServerID, response);
                        plugin.debug("Received response from " + responseServerID + " for request " + requestID);

                        if (requestData.getExpectedResponses().equals(requestData.getResponses().keySet())) {
                            requestData.getFuture().complete(requestData.getResponses());
                            pendingRequests.remove(requestID);
                            plugin.debug("Completed request " + requestID);
                        }
                    }
                }
            }
        };
        redisHandler.subscribe(responseSubscriber, "newsky-response-channel");
    }

    public CompletableFuture<ConcurrentHashMap<String, String>> sendRequest(String targetServer, String operation) {
        CompletableFuture<ConcurrentHashMap<String, String>> future = new CompletableFuture<>();
        String requestID = UUID.randomUUID().toString();
        Set<String> expectedResponses = targetServer.equals("all") ? heartBeatHandler.getActiveServers()
                : Set.of(targetServer);
        pendingRequests.put(requestID, new RequestData(future, expectedResponses));

        String requestMessage = String.join(":", "request", requestID, serverID, targetServer, operation);
        redisHandler.publish("newsky-request-channel", requestMessage);
        plugin.debug("Sent request " + requestID + " to " + targetServer + " with operation " + operation);

        future.orTimeout(30, TimeUnit.SECONDS).whenComplete((result, error) -> {
            pendingRequests.remove(requestID);
            plugin.debug("Timeout for request " + requestID);
        });

        return future;
    }

    private static class RequestData {
        private final CompletableFuture<ConcurrentHashMap<String, String>> future;
        private final Set<String> expectedResponses;
        private final ConcurrentHashMap<String, String> responses = new ConcurrentHashMap<>();

        public RequestData(CompletableFuture<ConcurrentHashMap<String, String>> future, Set<String> expectedResponses) {
            this.future = future;
            this.expectedResponses = expectedResponses;
        }

        public CompletableFuture<ConcurrentHashMap<String, String>> getFuture() {
            return future;
        }

        public Set<String> getExpectedResponses() {
            return expectedResponses;
        }

        public ConcurrentHashMap<String, String> getResponses() {
            return responses;
        }
    }
}
