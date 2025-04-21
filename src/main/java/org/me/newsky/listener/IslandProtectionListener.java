package org.me.newsky.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandProtectionListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final int halfSize;
    private final int islandCenterX;
    private final int islandCenterZ;

    public IslandProtectionListener(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.halfSize = config.getIslandSize() / 2;
        this.islandCenterX = 0;
        this.islandCenterZ = 0;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
            plugin.debug("Player " + event.getPlayer().getName() + " tried to break a block on an island they don't own.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
            plugin.debug("Player " + event.getPlayer().getName() + " tried to place a block on an island they don't own.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (event.getClickedBlock() != null && !canPlayerEdit(event.getPlayer(), event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
                plugin.debug("Player " + event.getPlayer().getName() + " tried to interact with a block on an island they don't own.");
            }
        }
    }

    private boolean canPlayerEdit(Player player, Location location) {
        // Check if the player has admin permissions
        if (player.hasPermission("newsky.admin.bypass")) {
            plugin.debug("Player " + player.getName() + " has admin permissions, bypassing island protection.");
            return true;
        }

        if (location.getWorld() == null || !location.getWorld().getName().startsWith("island-")) {
            plugin.debug("Location is not in an island world, allowing edit.");
            return true;
        }

        UUID islandUuid = UUID.fromString(location.getWorld().getName().substring(7));

        // Assuming islandCenter and halfSize are cached or constants
        int minX = islandCenterX - halfSize;
        int maxX = islandCenterX + halfSize;
        int minZ = islandCenterZ - halfSize;
        int maxZ = islandCenterZ + halfSize;

        if (location.getBlockX() < minX || location.getBlockX() > maxX || location.getBlockZ() < minZ || location.getBlockZ() > maxZ) {
            plugin.debug("Player " + player.getName() + " is outside the island boundary.");
            return false;
        }

        // Assuming members is a cached HashSet for quick lookup
        return cacheHandler.getIslandPlayers(islandUuid).contains(player.getUniqueId());
    }
}
