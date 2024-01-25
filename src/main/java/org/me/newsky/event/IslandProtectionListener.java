package org.me.newsky.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.me.newsky.cache.CacheHandler;

import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final CacheHandler cacheHandler;

    public IslandProtectionListener(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot break blocks that not in your island.");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot place blocks that not in your island.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && !canPlayerEdit(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot interact with blocks that not in your island.");
        }
    }

    private boolean canPlayerEdit(Player player, Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().startsWith("island-")) {
            return true;
        }

        UUID islandUuid = UUID.fromString(location.getWorld().getName().substring(7));

        int islandCenterX = 0;
        int islandCenterZ = 0;
        int halfSize = 50; // Half the size of the island (100x100 total)

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
