package org.me.newsky.redis;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RedisPublishRequest {

    public interface RequestCallback {
        void onRequestComplete();
    }

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final RedisHeartBeat redisHeartBeat;
    private final String serverID;

    private final Set<String> respondedServers = ConcurrentHashMap.newKeySet();
    private final Set<String> serversToWaitFor = ConcurrentHashMap.newKeySet();

    private RequestCallback callback;

    public RedisPublishRequest(NewSky plugin, ConfigHandler config, RedisHandler redisHandler, RedisHeartBeat redisHeartBeat) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.redisHeartBeat = redisHeartBeat;
        this.serverID = config.getServerName();
    }

    public void sendRequest(String operation) {
        String requestID = "Req-" + UUID.randomUUID();

        // Add all active servers to the list of servers to wait for
        serversToWaitFor.addAll(redisHeartBeat.getActiveServers());

        // Send the request
        redisHandler.publish("request-channel", serverID + ":" + requestID + ":" + operation);

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
                        if (callback != null) {
                            callback.onRequestComplete();
                        }
                    }
                }
            }
        };

        redisHandler.subscribe(responseSubscriber, "response-channel-" + serverID);

        // Use Bukkit's scheduler to handle servers that did not respond
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            // Here you can handle/log servers that did not respond if needed
            serversToWaitFor.forEach(serversToWaitFor::remove);

            if (!serversToWaitFor.isEmpty()) {
                responseSubscriber.unsubscribe();
                if (callback != null) {
                    callback.onRequestComplete();
                }
            }
        }, 100L);
    }

    public void setCallback(RequestCallback callback) {
        this.callback = callback;
    }
}
