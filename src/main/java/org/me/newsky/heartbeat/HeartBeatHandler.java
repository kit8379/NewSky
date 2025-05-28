package org.me.newsky.heartbeat;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

public class HeartBeatHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final String serverID;
    private final int heartbeatInterval;
    private BukkitTask heartbeatTask;

    public HeartBeatHandler(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.serverID = serverID;
        this.heartbeatInterval = config.getHeartbeatInterval();
    }

    public void start() {
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!config.isLobby()) {
                cacheHandler.updateActiveServer(serverID);
                plugin.debug(getClass().getSimpleName(), "Updated heartbeat for server: " + serverID);
            }

            plugin.debug(getClass().getSimpleName(), "Active servers before check: " + cacheHandler.getActiveServers().keySet());

            cacheHandler.getActiveServers().forEach((server, lastHeartbeat) -> {
                long lastSeen = Long.parseLong(lastHeartbeat);
                long now = System.currentTimeMillis();
                if (now - lastSeen > heartbeatInterval * 1000L * 2) {
                    cacheHandler.removeActiveServer(server);
                    plugin.debug(getClass().getSimpleName(), "Removed inactive server: " + server + " (last seen " + lastSeen + ")");
                }
            });
        }, 0, heartbeatInterval * 20L);
    }

    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }

        if (!config.isLobby()) {
            cacheHandler.removeActiveServer(serverID);
            plugin.debug(getClass().getSimpleName(), "Stopped heartbeat and removed server from active list: " + serverID);
        }
    }
}