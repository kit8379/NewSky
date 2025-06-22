package org.me.newsky.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldHandler;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class IslandUnloadScheduler {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final WorldHandler worldHandler;
    private final long checkIntervalTicks;
    private final Map<String, Long> inactiveWorlds = new ConcurrentHashMap<>();
    private final Queue<String> unloadQueue = new ConcurrentLinkedQueue<>();
    private BukkitTask checkTask;
    private BukkitTask unloadTask;

    private static final long unloadProcessIntervalTicks = 10L;


    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, RedisCache redisCache, WorldHandler worldHandler) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.worldHandler = worldHandler;
        this.checkIntervalTicks = config.getIslandUnloadInterval() * 20L;
    }

    public void start() {
        plugin.debug("IslandUnloadScheduler", "Starting unload scheduler with check interval: " + checkIntervalTicks + " ticks, unload process interval: " + unloadProcessIntervalTicks + " ticks.");
        checkTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndQueueWorlds, 0, checkIntervalTicks);
        unloadTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processNextUnload, 10L, unloadProcessIntervalTicks);
        plugin.debug("IslandUnloadScheduler", "Unload scheduler started.");
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            plugin.debug("IslandUnloadScheduler", "Stopped check task.");
        }
        if (unloadTask != null) {
            unloadTask.cancel();
            plugin.debug("IslandUnloadScheduler", "Stopped unload task.");
        }
        inactiveWorlds.clear();
        unloadQueue.clear();
    }

    private void checkAndQueueWorlds() {
        long currentTime = System.currentTimeMillis();
        plugin.debug("IslandUnloadScheduler", "Starting unload check pass at time: " + currentTime);

        for (World world : Bukkit.getServer().getWorlds()) {
            String worldName = world.getName();

            if (!IslandUtils.isIslandWorld(worldName)) {
                plugin.debug("IslandUnloadScheduler", "Skipping non-island world: " + worldName);
                continue;
            }

            if (!world.getPlayers().isEmpty()) {
                plugin.debug("IslandUnloadScheduler", "World '" + worldName + "' has players online. Skipping unload.");
                inactiveWorlds.remove(worldName);
                continue;
            }

            long lastInactiveTime = inactiveWorlds.getOrDefault(worldName, currentTime);
            long durationInactive = currentTime - lastInactiveTime;

            plugin.debug("IslandUnloadScheduler", "World '" + worldName + "' inactive for: " + durationInactive + " ms.");

            if (durationInactive > checkIntervalTicks * 50L) {
                if (!unloadQueue.contains(worldName)) {
                    unloadQueue.add(worldName);
                    plugin.debug("IslandUnloadScheduler", "Queued world for unload: " + worldName);
                }
            } else {
                inactiveWorlds.put(worldName, lastInactiveTime);
            }
        }

        plugin.debug("IslandUnloadScheduler", "Unload check pass completed. Queue size: " + unloadQueue.size());
    }

    private void processNextUnload() {
        String nextWorld = unloadQueue.poll();

        if (nextWorld != null) {
            plugin.debug("IslandUnloadScheduler", "Processing unload for world: " + nextWorld);
            worldHandler.unloadWorld(nextWorld).thenRun(() -> {
                plugin.debug("IslandUnloadScheduler", "Successfully unloaded world: " + nextWorld);
                inactiveWorlds.remove(nextWorld);
                redisCache.removeIslandLoadedServer(IslandUtils.nameToUUID(nextWorld));
                plugin.debug("IslandUnloadScheduler", "Removed world from Redis loaded server list: " + nextWorld);
            }).exceptionally(ex -> {
                plugin.severe("Failed to unload world: " + nextWorld, ex);
                return null;
            });
        }
    }
}