package org.me.newsky.heartbeat;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisCache;

import java.util.Map;

public class HeartBeatHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final RedisCache redisCache;
    private final String serverID;
    private final int heartbeatInterval;
    private BukkitTask heartbeatTask;

    public HeartBeatHandler(NewSky plugin, ConfigHandler config, RedisCache redisCache, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.redisCache = redisCache;
        this.serverID = serverID;
        this.heartbeatInterval = config.getHeartbeatInterval();
    }

    public void start() {
        plugin.debug("HeartBeatHandler", "Performing startup cleanup for server: " + serverID);
        redisCache.removeActiveServer(serverID);
        plugin.debug("HeartBeatHandler", "Cleanup complete.");

        plugin.debug("HeartBeatHandler", "Starting heartbeat task with interval: " + heartbeatInterval + " seconds.");
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            redisCache.updateActiveServer(serverID, config.isLobbyOnly());
            plugin.debug("HeartBeatHandler", "Sent heartbeat for server: " + serverID);
            plugin.debug("HeartBeatHandler", "Active servers check initiated: " + redisCache.getActiveServers().keySet());

            long now = System.currentTimeMillis();
            long threshold = now - (heartbeatInterval * 1000L * 2);

            Map<String, String> servers = redisCache.getActiveServers();
            servers.forEach((server, lastHeartbeat) -> {
                try {
                    long lastSeen = Long.parseLong(lastHeartbeat);
                    if (lastSeen < threshold) {
                        plugin.debug("HeartBeatHandler", "Detected dead server: " + server);
                        redisCache.removeActiveServer(server);
                        plugin.debug("HeartBeatHandler", "Removed active server entry for server: " + server);
                    }
                } catch (NumberFormatException e) {
                    plugin.severe("Invalid heartbeat timestamp for server: " + server, e);
                    redisCache.removeActiveServer(server);
                }
            });
        }, 0, heartbeatInterval * 20L);
        plugin.debug("HeartBeatHandler", "Heartbeat task started successfully.");
    }

    public void stop() {
        if (heartbeatTask != null) {
            plugin.debug("HeartBeatHandler", "Stopping heartbeat task for server: " + serverID);
            heartbeatTask.cancel();
            plugin.debug("HeartBeatHandler", "Heartbeat task stopped.");
        }

        plugin.debug("HeartBeatHandler", "Performing shutdown cleanup for server: " + serverID);
        redisCache.removeActiveServer(serverID);
        plugin.debug("HeartBeatHandler", "Cleanup complete.");
    }
}
