package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.cluster.ServerRegistry;

public class HeartbeatScheduler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final ServerRegistry serverRegistry;
    private final String serverID;
    private final int heartbeatInterval;
    private final int heartbeatTtlSeconds;

    private BukkitTask heartbeatTask;

    public HeartbeatScheduler(NewSky plugin, ConfigHandler config, ServerRegistry serverRegistry, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.serverRegistry = serverRegistry;
        this.serverID = serverID;
        this.heartbeatInterval = config.getHeartbeatInterval();
        this.heartbeatTtlSeconds = Math.max(heartbeatInterval * 3, heartbeatInterval + 5);
    }

    public void start() {
        if (heartbeatTask != null) {
            plugin.debug("HeartbeatScheduler", "Heartbeat task is already running. No action taken.");
            return;
        }

        plugin.debug("HeartbeatScheduler", "Performing startup cleanup for server: " + serverID);
        serverRegistry.removeActiveServer(serverID);
        plugin.debug("HeartbeatScheduler", "Startup cleanup complete.");

        plugin.debug("HeartbeatScheduler", "Starting heartbeat task with interval: " + heartbeatInterval + " seconds, ttl: " + heartbeatTtlSeconds + " seconds.");
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            serverRegistry.updateActiveServer(serverID, config.isLobbyOnly(), heartbeatTtlSeconds);
            serverRegistry.reapDeadServerClaims();
            plugin.debug("HeartbeatScheduler", "Sent heartbeat for server: " + serverID);
            plugin.debug("HeartbeatScheduler", "Active servers: " + serverRegistry.getActiveServers());
        }, 0L, heartbeatInterval * 20L);
        plugin.debug("HeartbeatScheduler", "Heartbeat task started successfully.");
    }

    public void stop() {
        if (heartbeatTask != null) {
            plugin.debug("HeartbeatScheduler", "Stopping heartbeat task for server: " + serverID);
            heartbeatTask.cancel();
            heartbeatTask = null;
            plugin.debug("HeartbeatScheduler", "Heartbeat task stopped.");
        }

        plugin.debug("HeartbeatScheduler", "Performing shutdown cleanup for server: " + serverID);
        serverRegistry.removeActiveServer(serverID);
        plugin.debug("HeartbeatScheduler", "Shutdown cleanup complete.");
    }
}