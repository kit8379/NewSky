package org.me.newsky.island;

import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisHeartBeat;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class IslandPublishRequest {

    private final Logger logger;
    private final RedisHandler redisHandler;
    private final RedisHeartBeat redisHeartBeat;
    private final String serverID;
    private final Set<String> serversToWaitFor = ConcurrentHashMap.newKeySet();

    public IslandPublishRequest(Logger logger, RedisHandler redisHandler, RedisHeartBeat redisHeartBeat, String serverID) {
        this.logger = logger;
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
        logger.info("Sent request " + requestID + " (" + serversToWaitFor.size() + " remaining)");

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Wait for response
        JedisPubSub responseSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String responderID = message;

                serversToWaitFor.remove(responderID);
                logger.info("Received response from " + responderID + " for request " + requestID + " (" + serversToWaitFor.size() + " remaining)");

                if (serversToWaitFor.isEmpty()) {
                    logger.info("Received all responses for request " + requestID);
                    this.unsubscribe();
                    future.complete(null);
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "newsky-response-channel-" + requestID);

        return future;
    }
}
