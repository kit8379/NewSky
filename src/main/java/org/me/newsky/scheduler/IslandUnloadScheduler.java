package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldActivityHandler;
import org.me.newsky.world.WorldHandler;

public class IslandUnloadScheduler {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final WorldHandler worldHandler;
    private final WorldActivityHandler worldActivityHandler;

    private final long unloadInterval;
    private BukkitTask task;

    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, RedisCache redisCache, WorldHandler worldHandler, WorldActivityHandler worldActivityHandler) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.worldHandler = worldHandler;
        this.worldActivityHandler = worldActivityHandler;
        this.unloadInterval = config.getIslandUnloadInterval();
    }

    public void start() {
        plugin.debug("IslandUnloadScheduler", "Starting unload scheduler with interval: " + unloadInterval + " seconds.");
        this.task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkInactiveWorlds, 0, unloadInterval * 20L);
        plugin.debug("IslandUnloadScheduler", "Unload scheduler started successfully.");
    }

    public void stop() {
        if (task != null) {
            plugin.debug("IslandUnloadScheduler", "Stopping unload scheduler.");
            task.cancel();
            plugin.debug("IslandUnloadScheduler", "Unload scheduler stopped.");
        }
    }

    private void checkInactiveWorlds() {
        plugin.debug("IslandUnloadScheduler", "Checking for inactive worlds...");
        long now = System.currentTimeMillis();
        long thresholdMillis = unloadInterval * 1000L;

        worldActivityHandler.getInactiveWorlds(thresholdMillis, now).forEach((worldName, timestamp) -> {
            plugin.debug("IslandUnloadScheduler", "Unloading inactive world: " + worldName);
            worldHandler.unloadWorld(worldName).thenRun(() -> {
                plugin.debug("IslandUnloadScheduler", "Unloaded world: " + worldName);
                redisCache.removeIslandLoadedServer(IslandUtils.nameToUUID(worldName));
            }).exceptionally(ex -> {
                plugin.severe("Failed to unload world: " + worldName, ex);
                return null;
            });
        });
    }
}