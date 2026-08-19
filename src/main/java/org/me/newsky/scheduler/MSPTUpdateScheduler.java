package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.cluster.ServerRegistry;

public class MSPTUpdateScheduler {

    private final NewSky plugin;
    private final ServerRegistry serverRegistry;
    private final String serverID;
    private final long updateInterval;
    private BukkitTask task;

    public MSPTUpdateScheduler(NewSky plugin, ConfigHandler config, ServerRegistry serverRegistry, String serverID) {
        this.plugin = plugin;
        this.serverRegistry = serverRegistry;
        this.serverID = serverID;
        this.updateInterval = config.getMsptUpdateInterval();
    }

    public void start() {
        if (task != null) {
            plugin.debug("MSPTUpdateScheduler", "MSPT update scheduler is already running. No action taken.");
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
            task = null;
            plugin.debug("MSPTUpdateScheduler", "MSPT update scheduler stopped.");
        }
    }

    private void updateMspt() {
        double mspt = Bukkit.getServer().getAverageTickTime();
        plugin.debug("MSPTUpdateScheduler", "Current MSPT: " + mspt);

        serverRegistry.updateServerMSPT(serverID, mspt);
        plugin.debug("MSPTUpdateScheduler", "Updated Redis with MSPT: " + mspt + " for serverID: " + serverID);
    }
}