package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldActivityHandler;

public class WorldActivityListener implements Listener {

    private final NewSky plugin;
    private final WorldActivityHandler worldActivityHandler;

    public WorldActivityListener(NewSky plugin, WorldActivityHandler worldActivityHandler) {
        this.plugin = plugin;
        this.worldActivityHandler = worldActivityHandler;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String world = event.getPlayer().getWorld().getName();
        if (IslandUtils.isIslandWorld(world)) {
            plugin.debug("WorldActivityListener", "Player " + event.getPlayer().getName() + " entered island world: " + world);
            worldActivityHandler.playerEnter(world);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String world = event.getPlayer().getWorld().getName();
        if (IslandUtils.isIslandWorld(world)) {
            plugin.debug("WorldActivityListener", "Player " + event.getPlayer().getName() + " left island world: " + world);
            worldActivityHandler.playerLeave(world, System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        String from = event.getFrom().getName();
        String to = event.getPlayer().getWorld().getName();
        long now = System.currentTimeMillis();

        if (IslandUtils.isIslandWorld(from)) {
            plugin.debug("WorldActivityListener", "Player " + event.getPlayer().getName() + " changed from island world: " + from + " to: " + to);
            worldActivityHandler.playerLeave(from, now);
        }
        if (IslandUtils.isIslandWorld(to)) {
            plugin.debug("WorldActivityListener", "Player " + event.getPlayer().getName() + " entered island world: " + to);
            worldActivityHandler.playerEnter(to);
        }
    }
}