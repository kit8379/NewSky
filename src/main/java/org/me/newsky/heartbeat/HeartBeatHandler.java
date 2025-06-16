package org.me.newsky.heartbeat;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisCache;
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

            redisCache.getActiveServers().forEach((server, lastHeartbeat) -> {
                long lastSeen = Long.parseLong(lastHeartbeat);
                long now = System.currentTimeMillis();
                if (now - lastSeen > heartbeatInterval * 1000L * 2) {
                    redisCache.removeActiveServer(server);
                }
            });
        }, 0, heartbeatInterval * 20L);
    }

    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
        }

        redisCache.removeActiveServer(serverID);
    }
}