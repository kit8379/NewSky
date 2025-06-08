package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandBanListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public IslandBanListener(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("newsky.admin.bypass")) {
            return;
        }
        if (event.getTo().getWorld() == null) {
            return;
        }

        if (!IslandUtils.isIslandWorld(event.getTo().getWorld().getName())) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(event.getTo().getWorld().getName());
        UUID playerUuid = player.getUniqueId();

        if (cacheHandler.isPlayerBanned(islandUuid, playerUuid)) {
            event.setCancelled(true);
            player.sendMessage(config.getPlayerBannedMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + player.getName() + " is banned from island " + islandUuid);
        }
    }
}
