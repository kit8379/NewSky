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
        // Start the heartbeat
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Update the server's heartbeat in the cache
            if (!config.isLobby()) {
                cacheHandler.updateActiveServer(serverID);
                plugin.debug("Updated server heartbeat");
            }

            // Check for inactive servers and remove them from the active list
            plugin.debug("Active servers before checking: " + cacheHandler.getActiveServers().keySet());
            cacheHandler.getActiveServers().forEach((server, lastHeartbeat) -> {
                if (System.currentTimeMillis() - Long.parseLong(lastHeartbeat) > heartbeatInterval * 1000L * 2) {
                    cacheHandler.removeActiveServer(server);
                    plugin.debug("Removed inactive server from active list: " + server);
                }
            });
        }, 0, heartbeatInterval * 20L);
    }


    public void stop() {
        // Stop the heartbeat
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }

        // Remove the server's heartbeat from the cache
        cacheHandler.removeActiveServer(serverID);
        plugin.debug("Removed server from active list");
    }
}
