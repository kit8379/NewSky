package org.me.newsky.event;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.teleport.TeleportManager;

import java.util.UUID;
import java.util.logging.Logger;

public class PlayerJoinListener implements Listener {

    private final Logger logger;
    private final TeleportManager teleportManager;

    public PlayerJoinListener(Logger logger, TeleportManager teleportManager) {
        this.logger = logger;
        this.teleportManager = teleportManager;
    }

    @EventHandler(priority= EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        logger.info("PlayerJoinEvent triggered for " + event.getPlayer().getName());

        UUID playerId = event.getPlayer().getUniqueId();

        Location pendingLocation = teleportManager.getPendingTeleport(playerId);

        logger.info("Pending location for " + playerId + " is " + pendingLocation);
        if (pendingLocation != null) {
            event.getPlayer().teleport(pendingLocation);
            logger.info("Teleporting " + playerId + " to pending location.");
            teleportManager.removePendingTeleport(playerId);
        }
    }
}
