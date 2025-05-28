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
        cacheHandler.addOnlinePlayer(event.getPlayer().getName(), serverID);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        cacheHandler.removeOnlinePlayer(event.getPlayer().getName(), serverID);
    }
}
