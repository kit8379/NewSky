package org.me.newsky.redis;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class RedisPubSubResponse {

    private final NewSky plugin;
    private final Logger logger;
    private final RedisHandler redisHandler;
    private final RedisHeartBeat redisHeartBeat;
    private final String serverID;


    public RedisPubSubResponse(NewSky plugin, Logger logger, ConfigHandler config, RedisHandler redisHandler, RedisHeartBeat redisHeartBeat) {
        this.plugin = plugin;
        this.logger = logger;
        this.redisHandler = redisHandler;
        this.serverID = config.getServerName();
        this.redisHeartBeat = redisHeartBeat;
    }

    public void sendRequest(String requestMessage) {
        String requestID = "Req-" + UUID.randomUUID();
        Set<String> respondedServers = ConcurrentHashMap.newKeySet();
        Set<String> serversToWaitFor = ConcurrentHashMap.newKeySet();

        // Add all active servers to the list of servers to wait for
        serversToWaitFor.addAll(redisHeartBeat.getActiveServers());

        // Send the request
        redisHandler.publish("request-channel", serverID + ":" + requestID + ":" + requestMessage);

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
                        proceedToNextOperation(respondedServers);
                    }
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "response-channel-" + serverID);

        // Use Bukkit's scheduler to handle servers that did not respond
        // Assuming you have access to the main plugin instance; if not, you might need to pass it to this class
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            // Here you can handle/log servers that did not respond if needed
            serversToWaitFor.forEach(serversToWaitFor::remove);

            if (!serversToWaitFor.isEmpty()) {
                responseSubscriber.unsubscribe();
                proceedToNextOperation(respondedServers);
            }
        }, 100L); // 5 seconds (20 ticks/second * 5)
    }


    private void proceedToNextOperation(Set<String> respondedServers) {
        // Handle operation after receiving response in here


    }

    private void subscribeToRequests() {
        JedisPubSub requestSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String senderID = parts[0];
                String requestID = parts[1];
                String operation = parts[2];

                // Process the request
                processRequest(operation);

                // Send response back to the sender
                redisHandler.publish("response-channel-" + senderID, serverID + ":" + requestID);
            }
        };

        redisHandler.subscribe(requestSubscriber, "request-channel");
    }

    private void processRequest(String operation) {
        switch (operation) {
            case "findworld":
                break;
            case "createworld":
                break;
            case "loadworld":
                break;
            case "unloadworld":
                break;
            case "deleteworld":
                break;
            case "teleportworld":
                break;
        }
    }
}
