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

public class TeleportRequestListener implements Listener {

    private final NewSky plugin;
    private final TeleportHandler teleportHandler;

    public TeleportRequestListener(NewSky plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        Location pendingLocation = teleportHandler.getPendingTeleport(playerUuid);
        if (pendingLocation != null) {
            player.teleportAsync(pendingLocation);
            teleportHandler.removePendingTeleport(playerUuid);
            plugin.debug("TeleportRequestListener", "Teleported " + player.getName() + " to pending location on join.");
        }
    }
}
