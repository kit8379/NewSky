package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldHandler;

import java.util.HashMap;
import java.util.Map;

public class WorldUnloadScheduler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final WorldHandler worldHandler;
    private final long worldUnloadInterval;
    private final Map<String, Long> inactiveWorlds = new HashMap<>();
    private BukkitTask unloadTask;

    public WorldUnloadScheduler(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, WorldHandler worldHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.worldHandler = worldHandler;
        this.worldUnloadInterval = config.getWorldUnloadInterval();
    }

    public void start() {
        unloadTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::checkAndUnloadWorlds, 0, worldUnloadInterval / 50);
    }

    public void stop() {
        if (unloadTask != null) {
            unloadTask.cancel();
        }
    }

    private void checkAndUnloadWorlds() {
        plugin.debug("Checking for inactive island worlds...");
        long currentTime = System.currentTimeMillis();
        plugin.getServer().getWorlds().forEach(world -> {
            if (world.getName().startsWith("island-") && world.getPlayers().isEmpty()) {
                long inactiveTime = inactiveWorlds.getOrDefault(world.getName(), currentTime);
                if (currentTime - inactiveTime > worldUnloadInterval) {
                    worldHandler.unloadWorld(world.getName()).thenRun(() -> {
                        plugin.debug("Unloaded inactive island world: " + world.getName());
                        inactiveWorlds.remove(world.getName());
                        cacheHandler.removeIslandLoadedServer(IslandUtils.nameToUUID(world.getName()));
                        plugin.debug("Removed inactive world from tracking: " + world.getName());
                    });
                } else {
                    inactiveWorlds.put(world.getName(), currentTime);
                }
            } else {
                inactiveWorlds.remove(world.getName());
            }
        });
        plugin.debug("Finished checking for inactive island worlds.");
    }
}
