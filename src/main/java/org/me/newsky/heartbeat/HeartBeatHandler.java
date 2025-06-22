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
        try {
            plugin.debug("HeartBeatHandler", "Performing startup cleanup for serverID: " + serverID);
            redisCache.removeActiveServer(serverID);
        } catch (Exception e) {
            plugin.severe("Failed to cleanup previous server state on startup", e);
        }

        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                redisCache.updateActiveServer(serverID, config.isLobby());

                long now = System.currentTimeMillis();
                long threshold = now - (heartbeatInterval * 1000L * 2);

                Map<String, String> servers = redisCache.getActiveServers();
                servers.forEach((server, lastHeartbeat) -> {
                    try {
                        long lastSeen = Long.parseLong(lastHeartbeat);
                        if (lastSeen < threshold) {
                            plugin.debug("HeartBeatHandler", "Detected dead server: " + server);
                            redisCache.removeActiveServer(server);
                        }
                    } catch (NumberFormatException e) {
                        plugin.severe("Invalid heartbeat timestamp for server: " + server, e);
                        redisCache.removeActiveServer(server);
                    }
                });

            } catch (Exception e) {
                plugin.severe("Exception in heartbeat task", e);
            }
        }, 0, heartbeatInterval * 20L);
    }

    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }

        try {
            redisCache.removeActiveServer(serverID);
        } catch (Exception e) {
            plugin.severe("Failed to cleanup server state on shutdown", e);
        }
    }
}
