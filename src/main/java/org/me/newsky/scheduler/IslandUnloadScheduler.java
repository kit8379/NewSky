package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.RedisCache;
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
        unloadTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndUnloadWorlds, 0, unloadInterval * 20L);
    }

    public void stop() {
        if (unloadTask != null) {
            unloadTask.cancel();
        }
    }

    private void checkAndUnloadWorlds() {
        long currentTime = System.currentTimeMillis();
        plugin.getServer().getWorlds().forEach(world -> {
            if (IslandUtils.isIslandWorld(world.getName()) && world.getPlayers().isEmpty()) {
                long inactiveTime = inactiveWorlds.getOrDefault(world.getName(), currentTime);
                if (currentTime - inactiveTime > unloadInterval) {
                    worldHandler.unloadWorld(world.getName()).thenRun(() -> {
                        inactiveWorlds.remove(world.getName());
                        redisCache.removeIslandLoadedServer(IslandUtils.nameToUUID(world.getName()));
                    });
                } else {
                    inactiveWorlds.put(world.getName(), currentTime);
                }
            } else {
                inactiveWorlds.remove(world.getName());
            }
        });
    }
}
