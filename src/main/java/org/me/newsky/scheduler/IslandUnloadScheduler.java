package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.network.IslandOperator;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldActivityHandler;

import java.util.UUID;

public class IslandUnloadScheduler {

    private final NewSky plugin;
    private final WorldActivityHandler worldActivityHandler;
    private final IslandOperator islandOperator;

    private final long unloadInterval;

    private BukkitTask task;

    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, WorldActivityHandler worldActivityHandler, IslandOperator islandOperator) {
        this.plugin = plugin;
        this.worldActivityHandler = worldActivityHandler;
        this.islandOperator = islandOperator;
        this.unloadInterval = config.getIslandUnloadInterval();
    }

    public void start() {
        if (task != null) {
            plugin.debug("IslandUnloadScheduler", "Unload scheduler is already running.");
            return;
        }

        plugin.debug("IslandUnloadScheduler", "Starting unload scheduler with interval: " + unloadInterval + " seconds.");
        this.task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkInactiveWorlds, 0L, unloadInterval * 20L);
        plugin.debug("IslandUnloadScheduler", "Unload scheduler started successfully.");
    }

    public void stop() {
        if (task != null) {
            plugin.debug("IslandUnloadScheduler", "Stopping unload scheduler.");
            task.cancel();
            task = null;
            plugin.debug("IslandUnloadScheduler", "Unload scheduler stopped.");
        }
    }

    private void checkInactiveWorlds() {
        plugin.debug("IslandUnloadScheduler", "Checking for inactive worlds...");
        long now = System.currentTimeMillis();
        long thresholdMillis = unloadInterval * 1000L;

        worldActivityHandler.getInactiveWorlds(thresholdMillis, now).forEach((worldName, timestamp) -> {
            UUID islandUuid = IslandUtils.parseIslandUuid(worldName);

            if (islandUuid == null) {
                worldActivityHandler.clearWorld(worldName);
                return;
            }

            islandOperator.unloadIsland(islandUuid).thenRun(() -> {
                plugin.debug("IslandUnloadScheduler", "Unloaded world: " + worldName);
            }).exceptionally(ex -> {
                plugin.severe("Failed to unload world: " + worldName, ex);
                return null;
            });
        });
    }
}
