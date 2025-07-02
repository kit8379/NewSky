package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisCache;

import java.util.UUID;

public class IslandCoopListener implements Listener {

    private final NewSky plugin;
    private final RedisCache redisCache;

    public IslandCoopListener(NewSky plugin, RedisCache redisCache) {
        this.plugin = plugin;
        this.redisCache = redisCache;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Delay to allow proxy switch to complete if applicable
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (!redisCache.getOnlinePlayersUUIDs().contains(playerUuid)) {
                plugin.getApi().removeAllCoopOfPlayer(playerUuid);
                plugin.debug("IslandCoopListener", "Removed all coop entries for player " + player.getName() + " on quit.");
            }
        }, 60L);
    }
}
