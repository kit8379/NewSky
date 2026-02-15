package org.me.newsky.listener;

import org.bukkit.GameRules;
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

        if (IslandUtils.isIslandWorld(worldName)) {
            // Set the spawn chunk radius to 0 can increase performance for world loading
            world.setGameRule(GameRules.RESPAWN_RADIUS, 0);
            plugin.debug("WorldInitListener", "Set spawn chunk radius to 0 for island world: " + worldName);
        }
    }
}