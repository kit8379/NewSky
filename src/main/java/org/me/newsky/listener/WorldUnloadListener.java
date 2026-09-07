package org.me.newsky.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.me.newsky.NewSky;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.world.WorldActivityHandler;

import java.util.UUID;

public final class WorldUnloadListener implements Listener {

    private final NewSky plugin;
    private final LevelUpdateScheduler levelUpdateScheduler;
    private final IslandSnapshot islandSnapshot;
    private final WorldActivityHandler worldActivityHandler;

    public WorldUnloadListener(NewSky plugin, LevelUpdateScheduler levelUpdateScheduler, IslandSnapshot islandSnapshot, WorldActivityHandler worldActivityHandler) {
        this.plugin = plugin;
        this.levelUpdateScheduler = levelUpdateScheduler;
        this.islandSnapshot = islandSnapshot;
        this.worldActivityHandler = worldActivityHandler;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        UUID islandUuid = IslandUtils.parseIslandUuid(worldName);
        if (islandUuid == null) {
            return;
        }

        levelUpdateScheduler.unregisterIsland(islandUuid);
        islandSnapshot.unload(islandUuid);
        worldActivityHandler.clearWorld(worldName);

        plugin.debug("WorldUnloadListener", "Unloaded island org.me.newsky.snapshot and unregistered level updates for island UUID: " + islandUuid);
    }
}
