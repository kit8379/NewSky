package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;
import org.me.newsky.teleport.TeleportHandler;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final NewSky plugin;
    private final TeleportHandler teleportHandler;

    public PlayerJoinListener(NewSky plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check if the player has a pending teleport
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        Location pendingLocation = teleportHandler.getPendingTeleport(playerUuid);
        plugin.debug("Pending location for " + playerUuid + " is " + pendingLocation);
        if (pendingLocation != null) {
            player.teleportAsync(pendingLocation);
            teleportHandler.removePendingTeleport(playerUuid);
            plugin.debug("Removed pending teleport for " + playerUuid);
        }
    }
}
