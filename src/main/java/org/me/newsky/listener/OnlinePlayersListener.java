package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.state.OnlinePlayerState;

import java.util.UUID;

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
        UUID playerUuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            onlinePlayerState.addOnlinePlayer(playerUuid, playerName, serverID);
            plugin.debug("OnlinePlayersListener", "Player " + playerName + " joined on server " + serverID);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            onlinePlayerState.removeOnlinePlayer(playerUuid);
            plugin.debug("OnlinePlayersListener", "Player " + playerName + " quit from server " + serverID);
        });
    }
}
