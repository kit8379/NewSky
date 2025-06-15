package org.me.newsky.listener;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.me.newsky.util.IslandUtils;

public class WorldInitListener implements Listener {

    public WorldInitListener() {
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void worldInit(WorldInitEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        if (IslandUtils.isIslandWorld(worldName)) {
            world.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0);
        }
    }
}
