package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;

public class OnlinePlayersListener implements Listener {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final String serverID;

    public OnlinePlayersListener(NewSky plugin, CacheHandler cacheHandler, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.serverID = serverID;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        // Add the player to the global online players cache with the server ID
        cacheHandler.addOnlinePlayer(event.getPlayer().getName(), serverID);
        plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " joined the server and was added to the online players cache.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        // Remove the player from the global online players cache with the server ID
        cacheHandler.removeOnlinePlayer(event.getPlayer().getName(), serverID);
        plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " left the server and was removed from the online players cache.");
    }
}
