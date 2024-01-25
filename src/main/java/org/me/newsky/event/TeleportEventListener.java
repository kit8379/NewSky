package org.me.newsky.event;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.me.newsky.cache.CacheHandler;

import java.util.UUID;

public class TeleportEventListener implements Listener {
    private final CacheHandler cacheHandler;

    public TeleportEventListener(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Check if the destination world is an island
        if (event.getTo() != null && event.getTo().getWorld() != null) {
            String worldName = event.getTo().getWorld().getName();
            if (worldName.startsWith("island-")) {
                UUID islandUuid = UUID.fromString(worldName.substring("island-".length()));
                boolean isLocked = cacheHandler.getIslandLock(islandUuid);
                if (isLocked) {
                    if (!cacheHandler.getIslandMembers(islandUuid).contains(event.getPlayer().getUniqueId())) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage("§cIsland is currently locked.");
                    }
                }
            }
        }
    }
}
