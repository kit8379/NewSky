package org.me.newsky.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.me.newsky.NewSky;

public class WorldEventListener implements Listener {

    private final NewSky plugin;

    public WorldEventListener(NewSky plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void worldInit(WorldInitEvent e) {

        String worldName = e.getWorld().getName();
        if (worldName.startsWith("island-")) {
            e.getWorld().setKeepSpawnInMemory(false);
            plugin.debug("World " + worldName + " set to not keep spawn in memory.");
        }
    }
}
