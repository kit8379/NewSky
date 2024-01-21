package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class UpdateSubscribeRequest {

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final IslandOperation islandOperation;
    private final String serverID;
    private JedisPubSub updateRequestSubscriber;

    public UpdateSubscribeRequest(NewSky plugin, RedisHandler redisHandler, IslandOperation islandOperation, String serverID) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.islandOperation = islandOperation;
        this.serverID = serverID;
    }

    public void subscribeToUpdateRequests() {
        updateRequestSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String requestID = parts[0];
                String operation = parts[2];

                if ("updateWorldList".equals(operation)) {
                    processUpdateRequest().thenAccept(responseData -> {
                        String responseString = String.join(",", responseData);
                        redisHandler.publish("newsky-update-response-channel-" + requestID, serverID + ":" + responseString);
                        plugin.debug("Sent update response back to request " + requestID + " to update response channel.");
                    });
                }
            }
        };

        redisHandler.subscribe(updateRequestSubscriber, "newsky-update-request-channel");
    }

    public void unsubscribeFromUpdateRequests() {
        if (updateRequestSubscriber != null) {
            updateRequestSubscriber.unsubscribe();
        }
    }

    private CompletableFuture<Set<String>> processUpdateRequest() {
        return islandOperation.updateWorldList()
                .thenApply(responseData -> {
                    plugin.debug("updateWorldList operation completed.");
                    // Splitting the string and converting it into a set
                    return new HashSet<>(Arrays.asList(responseData.split(",")));
                });
    }

}
