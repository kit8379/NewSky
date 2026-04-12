package org.me.newsky.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.me.newsky.NewSky;
import org.me.newsky.island.LimitHandler;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.util.IslandUtils;
import snapshot.IslandSnapshot;

import java.util.UUID;

public final class WorldUnloadListener implements Listener {

    private final NewSky plugin;
    private final LevelUpdateScheduler levelUpdateScheduler;
    private final IslandSnapshot islandSnapshot;
    private final LimitHandler limitHandler;

    public WorldUnloadListener(NewSky plugin, LevelUpdateScheduler levelUpdateScheduler, IslandSnapshot islandSnapshot, LimitHandler limitHandler) {
        this.plugin = plugin;
        this.levelUpdateScheduler = levelUpdateScheduler;
        this.islandSnapshot = islandSnapshot;
        this.limitHandler = limitHandler;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();

        if (!IslandUtils.isIslandWorld(worldName)) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(worldName);

        levelUpdateScheduler.unregisterIsland(islandUuid);
        islandSnapshot.unload(islandUuid);
        limitHandler.unload(islandUuid);

        plugin.debug("WorldUnloadListener", "Unloaded island snapshot and unregistered level updates for island UUID: " + islandUuid);
    }
}
