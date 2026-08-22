package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.network.IslandOperator;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldActivityHandler;

import java.util.UUID;

public class IslandUnloadScheduler {

    private final NewSky plugin;
    private final IslandOperator islandOperator;
    private final WorldActivityHandler worldActivityHandler;

    private final long unloadInterval;

    private BukkitTask task;

    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, IslandOperator islandOperator,
                                 WorldActivityHandler worldActivityHandler) {
        this.plugin = plugin;
        this.islandOperator = islandOperator;
        this.worldActivityHandler = worldActivityHandler;
        this.unloadInterval = config.getIslandUnloadInterval();
    }

    public void start() {
        if (task != null) {
            plugin.debug("IslandUnloadScheduler", "Unload scheduler is already running.");
            return;
        }

        plugin.debug("IslandUnloadScheduler", "Starting unload scheduler with interval: "
                + unloadInterval + " seconds.");
        this.task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::checkInactiveWorlds, 0L, unloadInterval * 20L);
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
            checkCandidateOnMainThread(worldName, thresholdMillis);
        });
    }

    private void checkCandidateOnMainThread(String worldName, long thresholdMillis) {
        UUID islandUuid = IslandUtils.parseIslandUuid(worldName);
        if (islandUuid == null) {
            worldActivityHandler.clearWorld(worldName);
            return;
        }

        Bukkit.getScheduler().getMainThreadExecutor(plugin).execute(() -> {
            unloadCandidateIfStillIdle(islandUuid, worldName, thresholdMillis);
        });
    }

    private void unloadCandidateIfStillIdle(UUID islandUuid, String worldName, long thresholdMillis) {
        if (!isStillInactive(worldName, thresholdMillis)) {
            plugin.debug("IslandUnloadScheduler", "World became active, skipping: " + worldName);
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            worldActivityHandler.clearWorld(worldName);
            return;
        }

        if (!world.getPlayers().isEmpty()) {
            plugin.debug("IslandUnloadScheduler", "World still has players, skipping: " + worldName);
            return;
        }

        islandOperator.unloadIslandIfIdle(islandUuid, () -> isStillInactive(worldName, thresholdMillis))
                .thenAccept(unloaded -> finishUnloadCheck(worldName, unloaded))
                .exceptionally(error -> {
                    plugin.severe("Failed to unload world: " + worldName, error);
                    return null;
                });
    }

    private boolean isStillInactive(String worldName, long thresholdMillis) {
        return worldActivityHandler.isStillInactive(
                worldName, thresholdMillis, System.currentTimeMillis());
    }

    private void finishUnloadCheck(String worldName, boolean unloaded) {
        if (unloaded) {
            worldActivityHandler.clearWorld(worldName);
            plugin.debug("IslandUnloadScheduler", "Unloaded world: " + worldName);
        } else {
            plugin.debug("IslandUnloadScheduler", "World became active while queued: " + worldName);
        }
    }
}
