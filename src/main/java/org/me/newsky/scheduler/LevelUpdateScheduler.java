package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.LevelHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class LevelUpdateScheduler {

    private final NewSky plugin;
    private final LevelHandler levelHandler;
    private final long updateInterval;
    private BukkitTask updateTask;

    public LevelUpdateScheduler(NewSky plugin, ConfigHandler config, LevelHandler levelHandler) {
        this.plugin = plugin;
        this.levelHandler = levelHandler;
        this.updateInterval = config.getIslandLevelUpdateInterval();
    }

    public void start() {
        plugin.debug("LevelUpdateScheduler", "Starting island level update scheduler with interval: " + updateInterval + " seconds.");

        updateTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::updateIslandLevels, 0, updateInterval * 20L);

        plugin.debug("LevelUpdateScheduler", "Island level update task scheduled successfully.");
    }

    public void stop() {
        if (updateTask != null) {
            plugin.debug("LevelUpdateScheduler", "Stopping island level update scheduler.");
            updateTask.cancel();
            plugin.debug("LevelUpdateScheduler", "Island level update scheduler stopped.");
        } else {
            plugin.debug("LevelUpdateScheduler", "No island level update task was running to stop.");
        }
    }

    private void updateIslandLevels() {
        plugin.debug("LevelUpdateScheduler", "Starting island level update pass.");

        for (World world : Bukkit.getServer().getWorlds()) {
            String worldName = world.getName();
            if (IslandUtils.isIslandWorld(worldName)) {
                UUID islandUuid = IslandUtils.nameToUUID(worldName);
                plugin.debug("LevelUpdateScheduler", "Calculating level for island UUID: " + islandUuid + " (world: " + worldName + ")");
                levelHandler.calIslandLevel(islandUuid);
            } else {
                plugin.debug("LevelUpdateScheduler", "Skipping non-island world: " + worldName);
            }
        }

        plugin.debug("LevelUpdateScheduler", "Island level update pass completed.");
    }
}