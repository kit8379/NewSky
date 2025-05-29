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
import org.me.newsky.util.IslandUtils;

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
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " attempted to break a block outside their island.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!canPlayerEdit(event.getPlayer(), event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
            plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " attempted to place a block outside their island.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (event.getClickedBlock() != null && !canPlayerEdit(event.getPlayer(), event.getClickedBlock().getLocation())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(config.getCannotEditIslandMessage());
                plugin.debug(getClass().getSimpleName(), "Player " + event.getPlayer().getName() + " attempted to interact with a block outside their island.");
            }
        }
    }

    private boolean canPlayerEdit(Player player, Location location) {
        // Check if the player has admin permissions
        if (player.hasPermission("newsky.admin.bypass")) {
            plugin.debug(getClass().getSimpleName(), "Player " + player.getName() + " has admin permissions, bypassing island protection checks.");
            return true;
        }

        if (location.getWorld() == null || !IslandUtils.isIslandWorld(location.getWorld().getName())) {
            plugin.debug(getClass().getSimpleName(), "Player " + player.getName() + " is in a non-island world, allowing edit.");
            return true;
        }

        UUID islandUuid = IslandUtils.nameToUUID(location.getWorld().getName());

        // Assuming islandCenter and halfSize are cached or constants
        int minX = islandCenterX - halfSize;
        int maxX = islandCenterX + halfSize;
        int minZ = islandCenterZ - halfSize;
        int maxZ = islandCenterZ + halfSize;

        if (location.getBlockX() < minX || location.getBlockX() > maxX || location.getBlockZ() < minZ || location.getBlockZ() > maxZ) {
            plugin.debug(getClass().getSimpleName(), "Player " + player.getName() + " attempted to edit a block outside their island boundaries.");
            return false;
        }

        return cacheHandler.getIslandPlayers(islandUuid).contains(player.getUniqueId());
    }
}
