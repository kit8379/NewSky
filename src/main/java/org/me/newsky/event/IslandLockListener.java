package org.me.newsky.event;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandLockListener implements Listener {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public IslandLockListener(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() - to.getBlockX() == 0 && from.getBlockZ() - to.getBlockZ() == 0)) {
            return;
        }

        if (to.getWorld() == null || !to.getWorld().getName().startsWith("island-")) {
            return;
        }

        UUID islandUuid = UUID.fromString(to.getWorld().getName().substring(7));
        if (cacheHandler.getIslandLock(islandUuid)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getIslandLockedMessage());
        }
    }
}