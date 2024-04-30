package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;
import org.me.newsky.teleport.TeleportRequestManager;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final NewSky plugin;
    private final TeleportRequestManager teleportRequestManager;

    public PlayerJoinListener(NewSky plugin, TeleportRequestManager teleportRequestManager) {
        this.plugin = plugin;
        this.teleportRequestManager = teleportRequestManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check if the player has a pending teleport
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        Location pendingLocation = teleportRequestManager.getPendingTeleport(playerUuid);
        plugin.debug("Pending location for " + playerUuid + " is " + pendingLocation);
        if (pendingLocation != null) {
            player.teleportAsync(pendingLocation);
            teleportRequestManager.removePendingTeleport(playerUuid);
            plugin.debug("Removed pending teleport for " + playerUuid);
        }
    }
}
