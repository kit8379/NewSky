package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisCache;
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

        plugin.debug("MSPTUpdateScheduler", "Initialized with update interval: " + updateInterval + " seconds, serverID: " + serverID);
    }

    public void start() {
        if (config.isLobby()) {
            plugin.debug("MSPTUpdateScheduler", "This server is lobby; MSPT update scheduler will not start.");
            return;
        }

        plugin.debug("MSPTUpdateScheduler", "Starting MSPT update scheduler with interval: " + updateInterval + " seconds.");

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateMspt, 0, updateInterval * 20L);

        plugin.debug("MSPTUpdateScheduler", "MSPT update task scheduled successfully.");
    }

    public void stop() {
        if (task != null) {
            plugin.debug("MSPTUpdateScheduler", "Stopping MSPT update scheduler.");
            task.cancel();
            plugin.debug("MSPTUpdateScheduler", "MSPT update scheduler stopped.");
        } else {
            plugin.debug("MSPTUpdateScheduler", "No MSPT update task was running to stop.");
        }
    }

    private void updateMspt() {
        double mspt = Bukkit.getServer().getAverageTickTime();
        plugin.debug("MSPTUpdateScheduler", "Current MSPT: " + mspt);

        redisCache.updateServerMSPT(serverID, mspt);
        plugin.debug("MSPTUpdateScheduler", "Updated Redis with MSPT: " + mspt + " for serverID: " + serverID);
    }
}