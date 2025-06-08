package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;

import java.util.Set;
import java.util.UUID;

public class IslandCoopListener implements Listener {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public IslandCoopListener(NewSky plugin, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        // Delay to allow proxy switch to complete if applicable
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            Set<String> onlinePlayers = cacheHandler.getOnlinePlayers();

            if (!onlinePlayers.contains(playerName)) {
                plugin.getApi().removeAllCoopOfPlayer(playerUuid);
                plugin.debug(getClass().getSimpleName(), "Cleared coop status for globally disconnected player: " + playerName);
            } else {
                plugin.debug(getClass().getSimpleName(), "Skipped coop clear for " + playerName + ", still online on another server.");
            }
        }, 60L);
    }
}
