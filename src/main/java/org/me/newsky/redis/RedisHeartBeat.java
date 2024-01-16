package org.me.newsky.redis;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
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
    private BukkitTask sendHeartbeatTask;
    private BukkitTask checkTimeoutsTask;

    // Store the last heartbeat time for each server.
    private final ConcurrentHashMap<String, Long> serverLastHeartbeat = new ConcurrentHashMap<>();

    public RedisHeartBeat(NewSky plugin, Logger logger, RedisHandler redisHandler, String serverID) {
        this.plugin = plugin;
        this.logger = logger;
        this.redisHandler = redisHandler;
        this.serverID = serverID;
    }

    public void startHeartBeat() {
        listenForHeartBeats();
        sendHeartbeatTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sendForHeartBeats, 0L, 100L);
        checkTimeoutsTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkServerTimeouts, 0L, 100L);
    }

    public void stopHeartBeat() {
        if (heartBeatSubscriber != null) {
            heartBeatSubscriber.unsubscribe();
        }
        if (sendHeartbeatTask != null) {
            sendHeartbeatTask.cancel();
        }
        if (checkTimeoutsTask != null) {
            checkTimeoutsTask.cancel();
        }
    }

    public void sendForHeartBeats() {
        // Send a heartbeat every 5 seconds.
        redisHandler.publish("newsky-heartbeat-channel", serverID);
        serverLastHeartbeat.put(serverID, System.currentTimeMillis());
        logger.info("Sent heartbeat");
    }

    public void listenForHeartBeats() {
        heartBeatSubscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                serverLastHeartbeat.put(message, System.currentTimeMillis());
                logger.info("Received heartbeat from " + message);
            }
        };

        redisHandler.subscribe(heartBeatSubscriber, "newsky-heartbeat-channel");
    }

    public void checkServerTimeouts() {
        long now = System.currentTimeMillis();
        serverLastHeartbeat.forEach((server, lastHeartbeat) -> {
            if (now - lastHeartbeat > 5000) {  // 5 seconds without heartbeat
                serverLastHeartbeat.remove(server);
                logger.info("Server " + server + " timed out. Removing from active servers.");
            }
        });
    }

    public Set<String> getActiveServers() {
        return serverLastHeartbeat.keySet();
    }
}
