package org.me.newsky.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.me.newsky.cache.CacheHandler;

import java.util.logging.Logger;

public class WorldEventListener implements Listener {

    private final Logger logger;
    private final CacheHandler cacheHandler;


    public WorldEventListener(Logger logger, CacheHandler cacheHandler) {
        this.logger = logger;
        this.cacheHandler = cacheHandler;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void worldInit(WorldInitEvent e) {
        String worldName = e.getWorld().getName();
        if(cacheHandler.isIslandWorld(worldName)) {
            e.getWorld().setKeepSpawnInMemory(false);
            logger.info("World " + worldName + " is a NewSky island. Set to not keep spawn in memory.");
        }
    }
}
