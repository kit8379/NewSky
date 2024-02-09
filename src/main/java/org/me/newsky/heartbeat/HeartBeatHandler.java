package org.me.newsky.heartbeat;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.JedisPubSub;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class HeartBeatHandler {

    private static final long HEARTBEAT_RATE_MS = TimeUnit.SECONDS.toMillis(5);
    private static final long TIMEOUT_MS = HEARTBEAT_RATE_MS * 2;
    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final String serverID;
    private final String serverMode;
    private final ConcurrentHashMap<String, Long> serverLastHeartbeat = new ConcurrentHashMap<>();
    private JedisPubSub heartBeatSubscriber;
    private BukkitTask combinedTask;

    public HeartBeatHandler(NewSky plugin, RedisHandler redisHandler, String serverID, String serverMode) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
        this.serverMode = serverMode;
    }

    public void startHeartBeat() {
        listenForHeartBeats();
        combinedTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::combinedHeartBeatTask, 0L, HEARTBEAT_RATE_MS / 50); // Convert to server ticks
        notifyServerStart();
    }

    public void stopHeartBeat() {
        if (heartBeatSubscriber != null) {
            heartBeatSubscriber.unsubscribe();
        }
        if (combinedTask != null) {
            combinedTask.cancel();
        }
        notifyServerStop();
    }

    private void combinedHeartBeatTask() {
        if (serverMode.equals("island")) {
            sendHeartBeats();
        }
        checkServerTimeouts();
    }

    private void sendHeartBeats() {
        redisHandler.publish("newsky-heartbeat-channel", serverID);
        serverLastHeartbeat.put(serverID, System.currentTimeMillis());
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
            if (now - entry.getValue() > TIMEOUT_MS) {
                return true;
            }
            return false;
        });
    }

    public Set<String> getActiveServers() {
        return serverLastHeartbeat.keySet();
    }

    private void notifyServerStart() {
        redisHandler.publish("newsky-server-status", serverID + ":start");
        serverLastHeartbeat.put(serverID, System.currentTimeMillis());
    }

    private void notifyServerStop() {
        redisHandler.publish("newsky-server-status", serverID + ":stop");
        serverLastHeartbeat.remove(serverID);
    }
}
