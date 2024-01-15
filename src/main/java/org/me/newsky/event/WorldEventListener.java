package org.me.newsky.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

import java.util.logging.Logger;
import java.util.regex.Pattern;

public class WorldEventListener implements Listener {

    private final Logger logger;
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public WorldEventListener(Logger logger) {
        this.logger = logger;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void worldInit(WorldInitEvent e) {
        String worldName = e.getWorld().getName();
        if(UUID_PATTERN.matcher(worldName).matches()) {
            e.getWorld().setKeepSpawnInMemory(false);
            logger.info("World " + worldName + " set to not keep spawn in memory.");
        }
    }
}
