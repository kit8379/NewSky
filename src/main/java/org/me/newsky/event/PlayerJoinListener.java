package org.me.newsky.event;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;
import org.me.newsky.teleport.TeleportManager;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final NewSky plugin;
    private final TeleportManager teleportManager;

    public PlayerJoinListener(NewSky plugin, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.teleportManager = teleportManager;
    }

    @EventHandler(priority= EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.debug("PlayerJoinEvent triggered for " + event.getPlayer().getName());

        UUID playerId = event.getPlayer().getUniqueId();
        Location pendingLocation = teleportManager.getPendingTeleport(playerId);
        plugin.debug("Pending location for " + playerId + " is " + pendingLocation);

        if (pendingLocation != null) {
            event.getPlayer().teleport(pendingLocation);
            plugin.debug("Teleporting " + playerId + " to pending location.");

            teleportManager.removePendingTeleport(playerId);
            plugin.debug("Removed pending teleport for " + playerId);
        }
    }
}
