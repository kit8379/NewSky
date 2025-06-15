package org.me.newsky.heartbeat;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.RedisCache;
import org.me.newsky.config.ConfigHandler;

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
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            redisCache.updateActiveServer(serverID, config.isLobby());
            plugin.debug(getClass().getSimpleName(), "Updated heartbeat for server: " + serverID);

            plugin.debug(getClass().getSimpleName(), "Active servers before check: " + redisCache.getActiveServers().keySet());
            plugin.debug(getClass().getSimpleName(), "Active game servers before check: " + redisCache.getActiveGameServers().keySet());

            redisCache.getActiveGameServers().forEach((server, lastHeartbeat) -> {
                long lastSeen = Long.parseLong(lastHeartbeat);
                long now = System.currentTimeMillis();
                if (now - lastSeen > heartbeatInterval * 1000L * 2) {
                    redisCache.removeActiveServer(server);
                    plugin.debug(getClass().getSimpleName(), "Removed inactive server: " + server + " (last seen " + lastSeen + ")");
                }
            });
        }, 0, heartbeatInterval * 20L);
    }

    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            plugin.debug(getClass().getSimpleName(), "Stopped heartbeat task for server: " + serverID);
        }

        redisCache.removeActiveServer(serverID);
        plugin.debug(getClass().getSimpleName(), "Removed server from active servers: " + serverID);
    }
}