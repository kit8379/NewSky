package org.me.newsky.redis;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class RedisHeartBeat {

    private final NewSky plugin;
    private final Logger logger;
    private final RedisHandler redisHandler;
    private final String serverID;

    private JedisPubSub heartBeatSubscriber;

    // Store the last heartbeat time for each server.
    private final ConcurrentHashMap<String, Long> serverLastHeartbeat = new ConcurrentHashMap<>();

    public RedisHeartBeat(NewSky plugin, Logger logger, ConfigHandler config, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.logger = logger;
        this.redisHandler = redisHandler;
        this.serverID = config.getServerName();
    }

    public void startHeartBeat() {
        listenForHeartBeats();
        sendForHeartBeats();
        checkServerTimeouts();
    }

    public void sendForHeartBeats() {
        // Send a heartbeat every 5 seconds.
        redisHandler.publish("newsky-heartbeat-channel", serverID);
        // Update our own last heartbeat timestamp
        serverLastHeartbeat.put(serverID, System.currentTimeMillis());

        // Schedule next heartbeat
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this::startHeartBeat, 100L);
    }

    public void listenForHeartBeats() {
        heartBeatSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                serverLastHeartbeat.put(message, System.currentTimeMillis());
            }
        };

        redisHandler.subscribe(heartBeatSubscriber, "heartbeat-channel");
    }

    public void checkServerTimeouts() {
        long now = System.currentTimeMillis();
        serverLastHeartbeat.forEach((server, lastHeartbeat) -> {
            if (now - lastHeartbeat > 5000) {  // 5 seconds without heartbeat
                serverLastHeartbeat.remove(server);
            }
        });

        // Schedule to check server timeouts again in the future
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this::checkServerTimeouts, 100L);
    }

    public Set<String> getActiveServers() {
        return serverLastHeartbeat.keySet();
    }

    public void stopHeartBeat() {
        if (heartBeatSubscriber != null) {
            heartBeatSubscriber.unsubscribe();
        }
    }
}
