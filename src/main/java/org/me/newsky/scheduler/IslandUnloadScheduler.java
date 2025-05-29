package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldHandler;

import java.util.HashMap;
import java.util.Map;

public class IslandUnloadScheduler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final WorldHandler worldHandler;
    private final long unloadInterval;
    private final Map<String, Long> inactiveWorlds = new HashMap<>();
    private BukkitTask unloadTask;

    public IslandUnloadScheduler(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, WorldHandler worldHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
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
        plugin.debug(getClass().getSimpleName(), "Starting scheduled task to check for inactive island worlds.");
        long currentTime = System.currentTimeMillis();
        plugin.getServer().getWorlds().forEach(world -> {
            if (world.getName().startsWith("island-") && world.getPlayers().isEmpty()) {
                long inactiveTime = inactiveWorlds.getOrDefault(world.getName(), currentTime);
                if (currentTime - inactiveTime > unloadInterval) {
                    worldHandler.unloadWorld(world.getName()).thenRun(() -> {
                        plugin.debug(getClass().getSimpleName(), "Unloaded inactive island world: " + world.getName());
                        inactiveWorlds.remove(world.getName());
                        cacheHandler.removeIslandLoadedServer(IslandUtils.nameToUUID(world.getName()));
                        plugin.debug(getClass().getSimpleName(), "Removed inactive world from tracking: " + world.getName());
                    });
                } else {
                    inactiveWorlds.put(world.getName(), currentTime);
                }
            } else {
                inactiveWorlds.remove(world.getName());
            }
        });
        plugin.debug(getClass().getSimpleName(), "Finished checking for inactive island worlds.");

    }
}
