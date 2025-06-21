package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.LevelHandler;
import org.me.newsky.util.IslandUtils;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LevelUpdateScheduler {

    private final NewSky plugin;
    private final LevelHandler levelHandler;
    private final long updateIntervalTicks;
    private final Queue<UUID> islandQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask refillTask;
    private BukkitTask processTask;

    private final static long islandProcessIntervalTicks = 10L;

    public LevelUpdateScheduler(NewSky plugin, ConfigHandler config, LevelHandler levelHandler) {
        this.plugin = plugin;
        this.levelHandler = levelHandler;
        this.updateIntervalTicks = config.getIslandLevelUpdateInterval() * 20L;
    }

    public void start() {
        plugin.debug("LevelUpdateScheduler", "Starting scalable level update scheduler.");
        refillTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refillQueue, 0, updateIntervalTicks);
        processTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processNextIsland, 1L, islandProcessIntervalTicks);
        plugin.debug("LevelUpdateScheduler", "Level update scheduler started with refill interval: " + updateIntervalTicks + " ticks, process interval: " + islandProcessIntervalTicks + " ticks.");
    }

    public void stop() {
        if (refillTask != null) {
            refillTask.cancel();
            plugin.debug("LevelUpdateScheduler", "Stopped refill task.");
        }
        if (processTask != null) {
            processTask.cancel();
            plugin.debug("LevelUpdateScheduler", "Stopped process task.");
        }
        islandQueue.clear();
    }

    private void refillQueue() {
        plugin.debug("LevelUpdateScheduler", "Refilling island level queue.");

        islandQueue.clear();

        for (World world : Bukkit.getServer().getWorlds()) {
            String worldName = world.getName();
            if (IslandUtils.isIslandWorld(worldName)) {
                UUID islandUuid = IslandUtils.nameToUUID(worldName);
                islandQueue.add(islandUuid);
                plugin.debug("LevelUpdateScheduler", "Queued island UUID: " + islandUuid + " (world: " + worldName + ")");
            }
        }

        plugin.debug("LevelUpdateScheduler", "Refill complete. Islands queued: " + islandQueue.size());
    }

    private void processNextIsland() {
        UUID nextIsland = islandQueue.poll();

        if (nextIsland != null) {
            plugin.debug("LevelUpdateScheduler", "Processing island level for: " + nextIsland);
            levelHandler.calIslandLevel(nextIsland);
        }
    }
}
