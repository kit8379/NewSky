package org.me.newsky.heartbeat;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.RuntimeCache;
import org.me.newsky.config.ConfigHandler;

public class HeartBeatHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final RuntimeCache runtimeCache;
    private final String serverID;
    private final int heartbeatInterval;
    private final int heartbeatTtlSeconds;

    private BukkitTask heartbeatTask;

    public HeartBeatHandler(NewSky plugin, ConfigHandler config, RuntimeCache runtimeCache, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.runtimeCache = runtimeCache;
        this.serverID = serverID;
        this.heartbeatInterval = config.getHeartbeatInterval();
        this.heartbeatTtlSeconds = Math.max(heartbeatInterval * 3, heartbeatInterval + 5);
    }

    public void start() {
        if (heartbeatTask != null) {
            plugin.debug("HeartBeatHandler", "Heartbeat task is already running. No action taken.");
            return;
        }

        plugin.debug("HeartBeatHandler", "Performing startup cleanup for server: " + serverID);
        runtimeCache.removeActiveServer(serverID);
        plugin.debug("HeartBeatHandler", "Startup cleanup complete.");

        plugin.debug("HeartBeatHandler", "Starting heartbeat task with interval: " + heartbeatInterval + " seconds, ttl: " + heartbeatTtlSeconds + " seconds.");

        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            runtimeCache.updateActiveServer(serverID, config.isLobbyOnly(), heartbeatTtlSeconds);
            plugin.debug("HeartBeatHandler", "Sent heartbeat for server: " + serverID);
        }, 0L, heartbeatInterval * 20L);

        plugin.debug("HeartBeatHandler", "Heartbeat task started successfully.");
    }

    public void stop() {
        if (heartbeatTask != null) {
            plugin.debug("HeartBeatHandler", "Stopping heartbeat task for server: " + serverID);
            heartbeatTask.cancel();
            plugin.debug("HeartBeatHandler", "Heartbeat task stopped.");
        }

        plugin.debug("HeartBeatHandler", "Performing shutdown cleanup for server: " + serverID);
        runtimeCache.removeActiveServer(serverID);
        plugin.debug("HeartBeatHandler", "Shutdown cleanup complete.");
    }
}