package org.me.newsky.heartbeat;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class HeartBeatHandler {


    private final NewSky plugin;
    private final ConfigHandler config;
    private final RedisHandler redisHandler;
    private final String serverID;
    private final boolean lobby;
    private final long heartbeatRateMs;
    private final ConcurrentHashMap<String, Long> serverLastHeartbeat = new ConcurrentHashMap<>();
    private JedisPubSub heartBeatSubscriber;
    private BukkitTask combinedTask;

    public HeartBeatHandler(NewSky plugin, ConfigHandler config, RedisHandler redisHandler, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
        this.lobby = config.isLobby();
        this.heartbeatRateMs = TimeUnit.SECONDS.toMillis(config.getHeartbeatInterval());
    }

    public void startHeartBeat() {
        listenForHeartBeats();
        if (!lobby) {
            combinedTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::sendHeartBeats, 0L, heartbeatRateMs / 50); // Convert to server ticks
        }
    }

    public void stopHeartBeat() {
        if (heartBeatSubscriber != null) {
            heartBeatSubscriber.unsubscribe();
        }
        if (combinedTask != null) {
            combinedTask.cancel();
        }
    }

    private void sendHeartBeats() {
        redisHandler.publish("newsky-heartbeat-channel", serverID);
        serverLastHeartbeat.put(serverID, System.currentTimeMillis());
        checkServerTimeouts();
    }

    private void listenForHeartBeats() {
        heartBeatSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                serverLastHeartbeat.put(message, System.currentTimeMillis());
            }
        };
        redisHandler.subscribe(heartBeatSubscriber, "newsky-heartbeat-channel");
    }

    private void checkServerTimeouts() {
        long now = System.currentTimeMillis();
        serverLastHeartbeat.entrySet().removeIf(entry -> {
            plugin.debug("Server " + entry.getKey() + " has timed out");
            return now - entry.getValue() > heartbeatRateMs;
        });
    }

    public Set<String> getActiveServers() {
        return serverLastHeartbeat.keySet();
    }
}
