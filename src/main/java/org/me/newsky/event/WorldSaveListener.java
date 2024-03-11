package org.me.newsky.event;

import org.bukkit.event.EventHandler;
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

    @EventHandler
    public void onWorldSave(WorldSaveEvent event) {
        plugin.debug("World save event triggered.");
        worldHandler.saveWorld(event.getWorld());
    }
}
