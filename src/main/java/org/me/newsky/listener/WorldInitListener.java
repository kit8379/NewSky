package org.me.newsky.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.util.IslandUtils;

public class WorldInitListener implements Listener {

    private final NewSky plugin;

    public WorldInitListener(NewSky plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void worldInit(WorldInitEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        // Check if the world is an island to set the spawn to not keep in memory
        if (IslandUtils.isIslandWorld(worldName)) {
            world.setKeepSpawnInMemory(false);
            plugin.debug("Island world " + worldName + " set to not keep spawn in memory.");
        }
    }
}
