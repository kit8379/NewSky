package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisCache;

public class OnlinePlayersListener implements Listener {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final String serverID;

    public OnlinePlayersListener(NewSky plugin, RedisCache redisCache, String serverID) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.serverID = serverID;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            redisCache.addOnlinePlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName(), serverID);
            plugin.debug("OnlinePlayersListener", "Player " + event.getPlayer().getName() + " joined on server " + serverID);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            redisCache.removeOnlinePlayer(event.getPlayer().getUniqueId());
            plugin.debug("OnlinePlayersListener", "Player " + event.getPlayer().getName() + " quit from server " + serverID);
        });
    }
}
