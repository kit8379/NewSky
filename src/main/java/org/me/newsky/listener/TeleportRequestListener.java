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
        Location pendingLocation = teleportHandler.removePendingTeleport(playerUuid);
        if (pendingLocation == null) {
            return;
        }

        if (!pendingLocation.isWorldLoaded()) {
            plugin.warning("Dropping pending teleport for " + player.getName() + ": target world is no longer loaded.");
            return;
        }

        player.teleportAsync(pendingLocation);
        plugin.debug("TeleportRequestListener", "Teleported " + player.getName() + " to pending location on join.");
    }
}