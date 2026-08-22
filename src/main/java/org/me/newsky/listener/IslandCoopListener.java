package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandCoopListener implements Listener {

    private final NewSky plugin;

    public IslandCoopListener(NewSky plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            plugin.getApi().getOnlinePlayersUUIDs().thenCompose(onlinePlayers -> {
                if (onlinePlayers.contains(playerUuid)) {
                    plugin.debug("IslandCoopListener", "Skipped coop cleanup for player " + playerName + " because they are still online.");
                    return CompletableFuture.completedFuture(null);
                }

                return plugin.getApi().removeAllCoopOfPlayer(playerUuid).thenRun(() -> {
                    plugin.debug("IslandCoopListener", "Removed all coop entries for player " + playerName + " on quit.");
                });
            }).exceptionally(ex -> {
                plugin.severe("Error removing coop entries for player " + playerName + " on quit.", ex);
                return null;
            });
        }, 60L);
    }
}