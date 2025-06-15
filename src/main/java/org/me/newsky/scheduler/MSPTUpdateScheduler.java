package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.RedisCache;
import org.me.newsky.config.ConfigHandler;

public class MSPTUpdateScheduler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final RedisCache redisCache;
    private final String serverID;
    private final long updateInterval;
    private BukkitTask task;

    public MSPTUpdateScheduler(NewSky plugin, ConfigHandler config, RedisCache redisCache, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.redisCache = redisCache;
        this.serverID = serverID;
        this.updateInterval = config.getMsptUpdateInterval();
    }

    public void start() {
        if (config.isLobby()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateMspt, 0, updateInterval * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void updateMspt() {
        double mspt = Bukkit.getServer().getAverageTickTime();
        redisCache.updateServerMSPT(serverID, mspt);
    }
}
