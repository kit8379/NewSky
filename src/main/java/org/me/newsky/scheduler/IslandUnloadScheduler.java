package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldActivityHandler;
import org.me.newsky.world.WorldHandler;

import java.util.UUID;

public class IslandUnloadScheduler {

    private final NewSky plugin;
    private final WorldHandler worldHandler;
    private final WorldActivityHandler worldActivityHandler;
    private final IslandRegistry islandRegistry;

    private final long unloadInterval;

    private BukkitTask task;

    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, WorldHandler worldHandler, WorldActivityHandler worldActivityHandler, IslandRegistry islandRegistry) {
        this.plugin = plugin;
        this.worldHandler = worldHandler;
        this.worldActivityHandler = worldActivityHandler;
        this.islandRegistry = islandRegistry;
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
            UUID islandUuid = IslandUtils.nameToUUID(worldName);

            World bukkitWorld = Bukkit.getWorld(worldName);

            if (bukkitWorld == null) {
                plugin.debug("IslandUnloadScheduler", "World is already absent in Bukkit. Clearing stale inactive entry: " + worldName);
                worldActivityHandler.clearWorld(worldName);
                return;
            }

            worldHandler.unloadWorld(worldName).thenRun(() -> {
                worldActivityHandler.clearWorld(worldName);
                islandRegistry.removeIslandLoadedServer(islandUuid);
                plugin.debug("IslandUnloadScheduler", "Unloaded world: " + worldName);
            }).exceptionally(ex -> {
                plugin.severe("Failed to unload world: " + worldName, ex);
                return null;
            });
        });
    }
}
