package org.me.newsky.event;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import org.me.newsky.NewSky;
import org.me.newsky.world.WorldHandler;

public class WorldSaveListener implements Listener {
    private final NewSky plugin;
    private final WorldHandler worldHandler;

    public WorldSaveListener(NewSky plugin, WorldHandler worldHandler) {
        this.plugin = plugin;
        this.worldHandler = worldHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWorldSave(WorldSaveEvent event) {
        // Check if the world is an island
        World world = event.getWorld();
        String worldName = world.getName();
        if (worldName.startsWith("island-")) {
            // Save the world
            worldHandler.saveWorld(event.getWorld());
            plugin.debug("Saving world " + event.getWorld().getName());
        }
    }
}
