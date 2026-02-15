package org.me.newsky.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.me.newsky.NewSky;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public final class WorldUnloadListener implements Listener {

    private final NewSky plugin;
    private final LevelUpdateScheduler levelUpdateScheduler;

    public WorldUnloadListener(NewSky plugin, LevelUpdateScheduler levelUpdateScheduler) {
        this.plugin = plugin;
        this.levelUpdateScheduler = levelUpdateScheduler;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        if (!IslandUtils.isIslandWorld(worldName)) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(worldName);

        unregisterLevelUpdate(islandUuid);
    }

    private void unregisterLevelUpdate(UUID islandUuid) {
        levelUpdateScheduler.unregisterIsland(islandUuid);
        plugin.debug("WorldUnloadListener", "Unregistered level update for island UUID: " + islandUuid);
    }
}
