package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldHandler;

import java.util.HashMap;
import java.util.Map;

public class IslandUnloadScheduler {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final WorldHandler worldHandler;
    private final long unloadInterval;
    private final Map<String, Long> inactiveWorlds = new HashMap<>();
    private BukkitTask unloadTask;

    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, RedisCache redisCache, WorldHandler worldHandler) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.worldHandler = worldHandler;
        this.unloadInterval = config.getIslandUnloadInterval();
    }

    public void start() {
        plugin.debug("IslandUnloadScheduler", "Starting island unload scheduler with interval: " + unloadInterval + " seconds.");
        unloadTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndUnloadWorlds, 0, unloadInterval * 20L);
        plugin.debug("IslandUnloadScheduler", "Island unload task scheduled successfully.");
    }

    public void stop() {
        if (unloadTask != null) {
            plugin.debug("IslandUnloadScheduler", "Stopping island unload scheduler.");
            unloadTask.cancel();
            plugin.debug("IslandUnloadScheduler", "Island unload scheduler stopped.");
        } else {
            plugin.debug("IslandUnloadScheduler", "No island unload task was running to stop.");
        }
    }

    private void checkAndUnloadWorlds() {
        long currentTime = System.currentTimeMillis();
        plugin.debug("IslandUnloadScheduler", "Starting unload check pass at time: " + currentTime);

        plugin.getServer().getWorlds().forEach(world -> {
            String worldName = world.getName();

            if (IslandUtils.isIslandWorld(worldName)) {
                if (world.getPlayers().isEmpty()) {
                    long inactiveTime = inactiveWorlds.getOrDefault(worldName, currentTime);
                    long durationInactive = currentTime - inactiveTime;

                    plugin.debug("IslandUnloadScheduler", "World '" + worldName + "' inactive for: " + durationInactive + " ms.");

                    if (durationInactive > unloadInterval * 1000L) {
                        plugin.debug("IslandUnloadScheduler", "Unloading world: " + worldName);
                        worldHandler.unloadWorld(worldName).thenRun(() -> {
                            plugin.debug("IslandUnloadScheduler", "Successfully unloaded world: " + worldName);
                            inactiveWorlds.remove(worldName);
                            redisCache.removeIslandLoadedServer(IslandUtils.nameToUUID(worldName));
                            plugin.debug("IslandUnloadScheduler", "Removed world from Redis loaded server list: " + worldName);
                        }).exceptionally(ex -> {
                            plugin.severe("Failed to unload world: " + worldName, ex);
                            return null;
                        });
                    } else {
                        inactiveWorlds.put(worldName, inactiveTime);
                    }
                } else {
                    plugin.debug("IslandUnloadScheduler", "World '" + worldName + "' has players online. Skipping unload.");
                    inactiveWorlds.remove(worldName);
                }
            } else {
                plugin.debug("IslandUnloadScheduler", "Skipping non-island world: " + worldName);
            }
        });

        plugin.debug("IslandUnloadScheduler", "Unload check pass completed.");
    }
}