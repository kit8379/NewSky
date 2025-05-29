package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

public class MSPTUpdateScheduler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final String serverID;
    private final long updateInterval;
    private BukkitTask task;

    public MSPTUpdateScheduler(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.serverID = serverID;
        this.updateInterval = config.getMsptUpdateInterval();
    }

    public void start() {
        if (config.isLobby()) {
            plugin.debug(getClass().getSimpleName(), "Skipping MSPT update scheduler in lobby mode.");
            return;
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateMspt, 0, updateInterval * 20L);
        plugin.debug(getClass().getSimpleName(), "Started MSPT update scheduler.");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            plugin.debug(getClass().getSimpleName(), "Stopped MSPT update scheduler.");
        }
    }

    private void updateMspt() {
        double mspt = Bukkit.getServer().getAverageTickTime();
        cacheHandler.updateServerMSPT(serverID, mspt);
        plugin.debug(getClass().getSimpleName(), "MSPT updated to Redis: " + mspt + " ms (" + serverID + ")");
    }
}
