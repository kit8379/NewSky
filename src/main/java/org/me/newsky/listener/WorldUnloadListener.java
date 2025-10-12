package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.me.newsky.NewSky;
import org.me.newsky.island.LimitHandler;
import org.me.newsky.util.IslandUtils;

public class WorldUnloadListener implements Listener {

    private final NewSky plugin;
    private final LimitHandler limitHandler;

    public WorldUnloadListener(NewSky plugin, LimitHandler limitHandler) {
        this.plugin = plugin;
        this.limitHandler = limitHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        String name = event.getWorld().getName();

        limitHandler.clear(IslandUtils.nameToUUID(name));
        plugin.debug("WorldLoadListener", "Unloaded world cache cleared: " + name);
    }
}
