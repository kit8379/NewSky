package org.me.newsky.heartbeat;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartBeatHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final RedisHandler redisHandler;
    private final String serverID;
    private final long heartbeatRateMs;
    private final ConcurrentHashMap<String, Long> serverLastHeartbeat = new ConcurrentHashMap<>();
    private final ScheduledExecutorService sendScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService checkScheduler = Executors.newSingleThreadScheduledExecutor();
    private JedisPubSub heartBeatSubscriber;

    public HeartBeatHandler(NewSky plugin, ConfigHandler config, RedisHandler redisHandler, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
        this.heartbeatRateMs = TimeUnit.SECONDS.toMillis(config.getHeartbeatInterval());
    }

    public void startHeartBeat() {
        // Listen for heartbeats from other servers
        listenForHeartBeats();

        // Check for server timeouts
        checkScheduler.scheduleWithFixedDelay(this::checkServerTimeouts, 0, heartbeatRateMs, TimeUnit.MILLISECONDS);

        // Only send heartbeats if the server is not a lobby
        if (!config.isLobby()) {
            sendScheduler.scheduleWithFixedDelay(this::sendHeartBeats, 0, heartbeatRateMs, TimeUnit.MILLISECONDS);
        }
    }

    public void stopHeartBeat() {
        // Send a message indicating that the server is stopping
        redisHandler.publish("newsky-heartbeat-channel", serverID + ":offline");
        plugin.debug("Sent offline message to Redis");

        // Shutdown the schedulers
        sendScheduler.shutdown();
        checkScheduler.shutdown();

        plugin.debug("Shutting down the heartbeat scheduler");

        if (heartBeatSubscriber != null) {
            heartBeatSubscriber.unsubscribe();
            plugin.debug("Unsubscribed from the heartbeat channel");
        }
    }

    private void sendHeartBeats() {
        // Send a message indicating that the server is online
        redisHandler.publish("newsky-heartbeat-channel", serverID + ":online");
        plugin.debug("Sent online message to Redis");

        // Check for server timeouts
        checkServerTimeouts();
    }

    private void listenForHeartBeats() {
        heartBeatSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                if (parts.length == 2) {
                    String server = parts[0];
                    String status = parts[1];
                    if (status.equals("online")) {
                        serverLastHeartbeat.put(server, System.currentTimeMillis());
                        plugin.debug("Received online message from " + server);
                    } else if (status.equals("offline")) {
                        serverLastHeartbeat.remove(server);
                        plugin.debug("Received offline message from " + server);
                    }
                }
            }
        };

        redisHandler.subscribe(heartBeatSubscriber, "newsky-heartbeat-channel");
        plugin.debug("Subscribed to the heartbeat channel");
    }

    private void checkServerTimeouts() {
        plugin.debug("Checking for server timeouts...");
        long now = System.currentTimeMillis();
        serverLastHeartbeat.entrySet().removeIf(entry -> now - entry.getValue() > heartbeatRateMs);
        plugin.debug("Active servers: " + serverLastHeartbeat.keySet());
        plugin.debug("Finished checking for server timeouts");
    }

    public Set<String> getActiveServers() {
        return serverLastHeartbeat.keySet();
    }
}
