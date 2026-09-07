package org.me.newsky.scheduler;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.cluster.ServerRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HeartbeatScheduler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final ServerRegistry serverRegistry;
    private final OnlinePlayerRegistry onlinePlayerRegistry;
    private final String serverID;
    private final int heartbeatInterval;
    private final int heartbeatTtlSeconds;

    private volatile long lastSuccessfulBeat;
    private BukkitTask heartbeatTask;

    public HeartbeatScheduler(NewSky plugin, ConfigHandler config, ServerRegistry serverRegistry, OnlinePlayerRegistry onlinePlayerRegistry, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.serverRegistry = serverRegistry;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
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
            long now = System.currentTimeMillis();

            try {
                serverRegistry.updateActiveServer(serverID, config.isLobbyOnly(), heartbeatTtlSeconds);
            } catch (Exception e) {
                plugin.severe("Failed to send heartbeat for server: " + serverID, e);
                return;
            }

            // A gap longer than the TTL means our heartbeat key expired, so a peer may
            // have reaped this server's online entries; re-register the players still here.
            if (lastSuccessfulBeat != 0L && now - lastSuccessfulBeat > heartbeatTtlSeconds * 1000L) {
                reRegisterOnlinePlayers();
            }
            lastSuccessfulBeat = now;

            plugin.debug("HeartbeatScheduler", "Sent heartbeat for server: " + serverID);
            plugin.debug("HeartbeatScheduler", "Active servers: " + serverRegistry.getActiveServers());

            try {
                reapDeadServers();
            } catch (Exception e) {
                plugin.severe("Failed to reap dead servers", e);
            }
        }, 0L, heartbeatInterval * 20L);
        plugin.debug("HeartbeatScheduler", "Heartbeat task started successfully.");
    }

    private void reRegisterOnlinePlayers() {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Map<UUID, String> players = new HashMap<>();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                players.put(player.getUniqueId(), player.getName());
            }

            if (players.isEmpty()) {
                return;
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                onlinePlayerRegistry.addAllOnlinePlayers(players, serverID);
                plugin.warning("Re-registered " + players.size() + " online players after a heartbeat gap.");
            });
        });
    }

    private void reapDeadServers() {
        for (String knownServer : serverRegistry.getKnownServers()) {
            if (knownServer.equals(serverID)) {
                continue;
            }

            long removed = serverRegistry.reapDeadServer(knownServer);
            if (removed > 0) {
                plugin.warning("Cleaned up state of dead server " + knownServer + ": removed " + removed + " stale entries.");
            } else if (removed == 0) {
                plugin.debug("HeartbeatScheduler", "Deregistered stopped server: " + knownServer);
            }
        }
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