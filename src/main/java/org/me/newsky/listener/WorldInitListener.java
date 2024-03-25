package org.me.newsky.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.me.newsky.NewSky;

public class WorldInitListener implements Listener {

    private final NewSky plugin;

    public WorldInitListener(NewSky plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void worldInit(WorldInitEvent event) {
        // Check if the world is an island
        World world = event.getWorld();
        String worldName = world.getName();
        if (worldName.startsWith("island-")) {
            // Set the world to not keep spawn in memory
            world.setKeepSpawnInMemory(false);
            plugin.debug("Island world " + worldName + " set to not keep spawn in memory.");
        }
    }
}
