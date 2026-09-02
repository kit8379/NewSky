package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;
import org.me.newsky.uuid.UuidHandler;

import java.util.UUID;

public class UuidUpdateListener implements Listener {

    private final NewSky plugin;
    private final UuidHandler uuidHandler;

    public UuidUpdateListener(NewSky plugin, UuidHandler uuidHandler) {
        this.plugin = plugin;
        this.uuidHandler = uuidHandler;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();

        uuidHandler.updatePlayerUuid(uuid, name).thenRun(() -> {
            plugin.debug("UuidUpdateListener", "Updated UUID for player: " + name + " (" + uuid + ")");
        }).exceptionally(ex -> {
            plugin.severe("Error updating UUID for player " + name + " (" + uuid + ")", ex);
            return null;
        });
    }
}
