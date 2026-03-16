package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.RuntimeCache;

public class OnlinePlayersListener implements Listener {

    private final NewSky plugin;
    private final RuntimeCache runtimeCache;
    private final String serverID;

    public OnlinePlayersListener(NewSky plugin, RuntimeCache runtimeCache, String serverID) {
        this.plugin = plugin;
        this.runtimeCache = runtimeCache;
        this.serverID = serverID;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            runtimeCache.addOnlinePlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
            plugin.debug("OnlinePlayersListener", "Player " + event.getPlayer().getName() + " joined on server " + serverID);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            runtimeCache.removeOnlinePlayer(event.getPlayer().getUniqueId());
            plugin.debug("OnlinePlayersListener", "Player " + event.getPlayer().getName() + " quit from server " + serverID);
        });
    }
}
