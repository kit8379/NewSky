package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.cache.RedisCache;

public class OnlinePlayersListener implements Listener {

    private final RedisCache redisCache;
    private final String serverID;

    public OnlinePlayersListener(RedisCache redisCache, String serverID) {
        this.redisCache = redisCache;
        this.serverID = serverID;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        redisCache.addOnlinePlayer(event.getPlayer().getName(), serverID);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        redisCache.removeOnlinePlayer(event.getPlayer().getName());
    }
}
