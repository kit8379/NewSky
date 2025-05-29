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
        updateTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::updateIslandLevels, 0, updateInterval * 20L);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    private void updateIslandLevels() {
        plugin.debug(getClass().getSimpleName(), "Starting scheduled task to update island levels.");
        for (World world : Bukkit.getServer().getWorlds()) {
            if (world.getName().startsWith("island-")) {
                UUID islandUuid = IslandUtils.nameToUUID(world.getName());
                levelHandler.calIslandLevel(islandUuid);
                plugin.debug(getClass().getSimpleName(), "Level updated for island: " + world.getName());
            }
        }
        plugin.debug(getClass().getSimpleName(), "Finished updating island levels.");
    }
}
