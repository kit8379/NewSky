package org.me.newsky.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;

import java.util.UUID;

public class UuidUpdateListener implements Listener {

    private final NewSky plugin;
    private final Cache cache;

    public UuidUpdateListener(NewSky plugin, Cache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            cache.updatePlayerUuid(uuid, name);
            plugin.debug("UuidUpdateListener", "Updated UUID for player: " + name + " (" + uuid + ")");
        });
    }
}
