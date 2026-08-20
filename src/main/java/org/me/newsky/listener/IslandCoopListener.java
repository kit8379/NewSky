package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.island.CoopHandler;

import java.util.UUID;

/**
 * Coop is trust granted to someone currently visiting, so it ends when they leave the cluster.
 * Internal machinery: calls its handler directly rather than going through the public API.
 */
public class IslandCoopListener implements Listener {

    // Long enough for a proxy server switch to re-register the player on their new server, so a
    // transfer is not mistaken for a disconnect.
    private static final long CLEANUP_DELAY_TICKS = 60L;

    private final NewSky plugin;
    private final CoopHandler coopHandler;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public IslandCoopListener(NewSky plugin, CoopHandler coopHandler, OnlinePlayerRegistry onlinePlayerRegistry) {
        this.plugin = plugin;
        this.coopHandler = coopHandler;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (onlinePlayerRegistry.isOnline(playerUuid)) {
                plugin.debug("IslandCoopListener", "Skipped coop cleanup for player " + playerName + " because they are still online.");
                return;
            }

            coopHandler.removeAllCoops(playerUuid).thenRun(() -> {
                plugin.debug("IslandCoopListener", "Removed all coop entries for player " + playerName + " on quit.");
            }).exceptionally(ex -> {
                plugin.severe("Error removing coop entries for player " + playerName + " on quit.", ex);
                return null;
            });
        }, CLEANUP_DELAY_TICKS);
    }
}
