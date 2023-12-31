package org.me.newsky.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.me.newsky.cache.CacheHandler;

import java.util.logging.Logger;

public class WorldEventListener implements Listener {

    private final CacheHandler cacheHandler;
    private final Logger logger;

    public WorldEventListener(CacheHandler cacheHandler, Logger logger) {
        this.cacheHandler = cacheHandler;
        this.logger = logger;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void worldInit(WorldInitEvent e) {
        String worldName = e.getWorld().getName();
        if(cacheHandler.isIslandWorld(worldName)) {
            e.getWorld().setKeepSpawnInMemory(false);
            logger.info("World " + worldName + " set to not keep spawn in memory.");
        }
    }
}
