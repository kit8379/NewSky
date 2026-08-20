package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.me.newsky.NewSky;
import org.me.newsky.uuid.UuidHandler;

import java.util.UUID;

/**
 * Feeds the system-owned name cache on join. Internal machinery: calls its handler directly
 * rather than going through the public API.
 */
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

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            uuidHandler.setPlayerName(uuid, name);
            plugin.debug("UuidUpdateListener", "Updated UUID for player: " + name + " (" + uuid + ")");
        });
    }
}
