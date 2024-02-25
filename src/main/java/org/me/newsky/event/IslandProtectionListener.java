package org.me.newsky.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final int halfSize;
    private final int islandCenterX;
    private final int islandCenterZ;

    public IslandProtectionListener(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.halfSize = config.getIslandSize() / 2;
        this.islandCenterX = 0;
        this.islandCenterZ = 0;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && !canPlayerEdit(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
        }
    }

    private boolean canPlayerEdit(Player player, Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().startsWith("island-")) {
            return true;
        }

        UUID islandUuid = UUID.fromString(location.getWorld().getName().substring(7));

        // Assuming islandCenter and halfSize are cached or constants
        int minX = islandCenterX - halfSize;
        int maxX = islandCenterX + halfSize;
        int minZ = islandCenterZ - halfSize;
        int maxZ = islandCenterZ + halfSize;

        if (location.getBlockX() < minX || location.getBlockX() > maxX ||
                location.getBlockZ() < minZ || location.getBlockZ() > maxZ) {
            return false;
        }

        // Assuming members is a cached HashSet for quick lookup
        return cacheHandler.getIslandMembers(islandUuid).contains(player.getUniqueId());
    }
}
