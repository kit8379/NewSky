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

public class IslandCoopListener implements Listener {

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
            try {
                if (onlinePlayerRegistry.isOnline(playerUuid)) {
                    plugin.debug("IslandCoopListener", "Skipped coop cleanup for player " + playerName + " because they are still online.");
                    return;
                }

                coopHandler.deleteAllCoopOfPlayer(playerUuid).thenRun(() -> {
                    plugin.debug("IslandCoopListener", "Removed all coop entries for player " + playerName + " on quit.");
                }).exceptionally(ex -> {
                    plugin.severe("Error removing coop entries for player " + playerName + " on quit.", ex);
                    return null;
                });
            } catch (RuntimeException ex) {
                plugin.severe("Error checking online state for player " + playerName + " on quit.", ex);
            }
        }, 100L);
    }
}
