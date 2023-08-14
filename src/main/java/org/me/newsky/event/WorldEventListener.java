package org.me.newsky.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.me.newsky.NewSky;

import java.util.UUID;

public class WorldEventListener implements Listener {

    private final NewSky plugin;

    public WorldEventListener(NewSky plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void worldInit(WorldInitEvent e) {
        String worldName = e.getWorld().getName();

        if (isUUIDFormat(worldName)) {
            e.getWorld().setKeepSpawnInMemory(false);
            plugin.getLogger().info("World " + worldName + " set to not keep spawn in memory.");
        }
    }

    // Utility method to check if string is in UUID format
    private boolean isUUIDFormat(String string) {
        try {
            UUID uuid = UUID.fromString(string);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

}
