// MODIFIED FILE: IslandUnloadScheduler.java
package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.network.lock.IslandOpLock;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldActivityHandler;
import org.me.newsky.world.WorldHandler;

import java.util.UUID;

public class IslandUnloadScheduler {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final WorldHandler worldHandler;
    private final WorldActivityHandler worldActivityHandler;
    private final IslandOpLock islandOpLock;

    private final long unloadInterval;

    private BukkitTask task;

    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, RedisCache redisCache, WorldHandler worldHandler, WorldActivityHandler worldActivityHandler, IslandOpLock islandOpLock) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.worldHandler = worldHandler;
        this.worldActivityHandler = worldActivityHandler;
        this.islandOpLock = islandOpLock;
        this.unloadInterval = config.getIslandUnloadInterval();
    }

    public void start() {
        if (task != null) {
            plugin.debug("IslandUnloadScheduler", "Unload scheduler is already running.");
            return;
        }

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
            UUID islandUuid = IslandUtils.nameToUUID(worldName);

            islandOpLock.withLock(islandUuid, () -> {
                plugin.debug("IslandUnloadScheduler", "Unloading inactive world under lock: " + worldName);

                return worldHandler.unloadWorld(worldName).thenRun(() -> {
                    worldActivityHandler.clearWorld(worldName);
                    redisCache.removeIslandLoadedServer(islandUuid);
                    plugin.debug("IslandUnloadScheduler", "Unloaded world: " + worldName);
                });
            }).exceptionally(ex -> {
                Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
                if (cause instanceof IslandBusyException) {
                    plugin.debug("IslandUnloadScheduler", "Skip unload because of busy lock: " + worldName);
                    return null;
                }
                plugin.severe("Failed to unload world: " + worldName, ex);
                return null;
            });
        });
    }
}