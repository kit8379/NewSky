package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;

import java.util.UUID;

public class UuidUpdateListener implements Listener {

    private final NewSky plugin;

    public UuidUpdateListener(NewSky plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getApi().updatePlayerUuid(uuid, name);
            plugin.debug("UuidUpdateListener", "Updated UUID for player: " + name + " (" + uuid + ")");
        });
    }
}
