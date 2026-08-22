package org.me.newsky.listener;

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
        TeleportHandler.PendingTeleportTicket pending = teleportHandler.claimPendingTeleport(playerUuid);
        if (pending == null) {
            return;
        }

        player.teleportAsync(pending.location()).whenComplete((teleported, error) -> {
            boolean arrived = error == null && Boolean.TRUE.equals(teleported);
            teleportHandler.completePendingTeleport(playerUuid, pending, arrived);

            if (arrived) {
                plugin.debug("TeleportRequestListener", "Teleported " + player.getName()
                        + " to pending location on join.");
            } else if (error != null) {
                plugin.severe("Pending teleport failed for " + player.getName()
                        + "; request retained for retry", error);
            } else {
                plugin.warning("Pending teleport was blocked for " + player.getName()
                        + "; request retained for retry");
            }
        });
    }
}
