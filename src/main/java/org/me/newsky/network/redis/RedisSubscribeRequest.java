package org.me.newsky.network.redis;

import org.me.newsky.NewSky;
import org.me.newsky.island.middleware.PostIslandHandler;
import org.me.newsky.network.BaseSubscribeRequest;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

public class RedisSubscribeRequest extends BaseSubscribeRequest {

    private JedisPubSub requestSubscriber;

    public RedisSubscribeRequest(NewSky plugin, RedisHandler redisHandler, String serverID, PostIslandHandler postIslandHandler) {
        super(plugin, redisHandler, serverID, postIslandHandler);
    }

    @Override
    public void subscribeToRequestChannel() {
        requestSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String messageType = parts[0];
                String requestID = parts[1];
                String sourceServer = parts[2];
                String targetServer = parts[3];
                String operation = parts[4];

                String[] args = new String[parts.length - 5];
                System.arraycopy(parts, 5, args, 0, args.length);

                if (messageType.equals("request") && targetServer.equals(serverID)) {
                    plugin.debug("Received request from " + sourceServer + " for " + operation + " with request ID " + requestID);
                    processRequest(operation, args).thenRun(() -> {
                        String responseMessage = String.join(":", "response", requestID, serverID, sourceServer);
                        redisHandler.publish("newsky-response-channel", responseMessage);
                        plugin.debug("Sent success response back to " + sourceServer + " for request ID " + requestID);
                    });
                }
            }
        };

        redisHandler.subscribe(requestSubscriber, "newsky-request-channel");
    }

    @Override
    public void unsubscribeFromRequestChannel() {
        requestSubscriber.unsubscribe();
    }
}
