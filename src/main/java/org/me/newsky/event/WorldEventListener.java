package org.me.newsky.event;

import org.bukkit.World;
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
        // Check if the world is an island
        World world = e.getWorld();
        String worldName = world.getName();
        if (worldName.startsWith("island-")) {
            world.setKeepSpawnInMemory(false);
            plugin.debug("Island world " + worldName + " set to not keep spawn in memory.");
        }
    }
}
