package org.me.newsky.network.redis;

import org.me.newsky.NewSky;
import org.me.newsky.network.BasePublishRequest;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class RedisPublishRequest extends BasePublishRequest {

    private JedisPubSub responseSubscriber;

    public RedisPublishRequest(NewSky plugin, RedisHandler redisHandler, String serverID) {
        super(plugin, redisHandler, serverID);
    }

    @Override
    public void subscribeToResponseChannel() {
        responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String messageType = parts[0];
                String requestID = parts[1];

                if (messageType.equals("response")) {
                    plugin.debug("Received success response for request ID " + requestID);
                    CompletableFuture<Void> future = pendingRequests.get(requestID);
                    if (future != null) {
                        future.complete(null);
                        pendingRequests.remove(requestID);
                    }
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "newsky-response-channel");
    }


    @Override
    public void unsubscribeFromResponseChannel() {
        responseSubscriber.unsubscribe();
    }


    @Override
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

        return future;
    }

}
