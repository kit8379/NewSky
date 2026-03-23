package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.state.OnlinePlayerState;

public class OnlinePlayersListener implements Listener {

    private final NewSky plugin;
    private final OnlinePlayerState onlinePlayerState;
    private final String serverID;

    public OnlinePlayersListener(NewSky plugin, OnlinePlayerState onlinePlayerState, String serverID) {
        this.plugin = plugin;
        this.onlinePlayerState = onlinePlayerState;
        this.serverID = serverID;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            onlinePlayerState.addOnlinePlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
            plugin.debug("OnlinePlayersListener", "Player " + event.getPlayer().getName() + " joined on server " + serverID);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            onlinePlayerState.removeOnlinePlayer(event.getPlayer().getUniqueId());
            plugin.debug("OnlinePlayersListener", "Player " + event.getPlayer().getName() + " quit from server " + serverID);
        });
    }
}
