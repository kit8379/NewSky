package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandLockListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public IslandLockListener(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getPlayer().hasPermission("newsky.admin.bypass")) {
            return;
        }

        if (event.getTo().getWorld() == null) {
            return;
        }

        if (!IslandUtils.isIslandWorld(event.getTo().getWorld().getName())) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(event.getTo().getWorld().getName());
        UUID playerUuid = event.getPlayer().getUniqueId();

        if (cacheHandler.isIslandLock(islandUuid) && !cacheHandler.getIslandPlayers(islandUuid).contains(playerUuid)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getIslandLockedMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " tried to enter locked island " + islandUuid);
        }
    }
}
