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

    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, IslandOperator islandOperator, WorldActivityHandler worldActivityHandler) {
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
                // A malformed island-prefixed name must not abort the sweep for every other world.
                worldActivityHandler.clearWorld(worldName);
                return;
            }

            // The async sweep only nominates candidates. The decision runs on the main thread,
            // right before the unload: a player may have entered since the sweep read its
            // timestamps, and Bukkit world state must not be touched from an async thread anyway.
            Bukkit.getScheduler().getMainThreadExecutor(plugin).execute(() -> {
                if (!worldActivityHandler.isStillInactive(worldName, thresholdMillis, System.currentTimeMillis())) {
                    plugin.debug("IslandUnloadScheduler", "World became active again before unload, skipping: " + worldName);
                    return;
                }

                World bukkitWorld = Bukkit.getWorld(worldName);

                if (bukkitWorld == null) {
                    plugin.debug("IslandUnloadScheduler", "World is already absent in Bukkit. Clearing stale inactive entry: " + worldName);
                    worldActivityHandler.clearWorld(worldName);
                    return;
                }

                if (!bukkitWorld.getPlayers().isEmpty()) {
                    // Authoritative guard: whatever the counters say, a world with players in it
                    // is not idle and unloading it would kick them.
                    plugin.debug("IslandUnloadScheduler", "World has players despite idle counters, skipping unload: " + worldName);
                    return;
                }

                // The predicate is evaluated again inside the island lifecycle slot, in the same
                // main-thread turn as Bukkit's unload. The checks above are only an inexpensive
                // early veto; they are not the correctness boundary.
                islandOperator.unloadIslandIfIdle(islandUuid,
                        () -> worldActivityHandler.isStillInactive(worldName, thresholdMillis, System.currentTimeMillis())).thenAccept(unloaded -> {
                    if (unloaded) {
                        worldActivityHandler.clearWorld(worldName);
                        plugin.debug("IslandUnloadScheduler", "Unloaded world: " + worldName);
                    } else {
                        plugin.debug("IslandUnloadScheduler", "World became active while queued for unload: " + worldName);
                    }
                }).exceptionally(ex -> {
                    plugin.severe("Failed to unload world: " + worldName, ex);
                    return null;
                });
            });
        });
    }
}
