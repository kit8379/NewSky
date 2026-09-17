package org.me.newsky.listener;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.LimitHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

/**
 * Enforces limits.yml on island worlds. Runs after protection has had its say, so a placement
 * outside the border or by a non-member is already cancelled here. Break and burn release a
 * block's count; anything else that removes blocks is caught up by the next level scan.
 */
public final class IslandLimitListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final LimitHandler limitHandler;

    public IslandLimitListener(NewSky plugin, ConfigHandler config, LimitHandler limitHandler) {
        this.plugin = plugin;
        this.config = config;
        this.limitHandler = limitHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Material type = block.getType();
        int limit = limitHandler.getBlockLimit(type);
        if (limit == LimitHandler.UNLIMITED) {
            return;
        }

        UUID islandUuid = IslandUtils.parseIslandUuid(block.getWorld().getName());
        if (islandUuid == null) {
            return;
        }

        if (!limitHandler.isReady(islandUuid)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getBlockLimitCalculatingMessage());
            plugin.debug("IslandLimitListener", "Denied block place before first scan: island=" + islandUuid + ", type=" + type + ", player=" + event.getPlayer().getUniqueId());
            return;
        }

        if (!limitHandler.tryIncrement(islandUuid, type)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(config.getBlockLimitReachedMessage(type.name(), limit));
            plugin.debug("IslandLimitListener", "Denied block place due to limit: island=" + islandUuid + ", type=" + type + ", player=" + event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        release(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        release(event.getBlock());
    }

    private void release(Block block) {
        Material type = block.getType();
        if (limitHandler.getBlockLimit(type) == LimitHandler.UNLIMITED) {
            return;
        }

        UUID islandUuid = IslandUtils.parseIslandUuid(block.getWorld().getName());
        if (islandUuid == null) {
            return;
        }

        limitHandler.decrement(islandUuid, type);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        denyIfFull(event, event.getLocation().getWorld(), event.getEntityType());
    }

    /** Vehicles, armor stands and end crystals placed by a player. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        denyIfFull(event, event.getBlock().getWorld(), event.getEntityType());
    }

    /** Item frames and paintings. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        denyIfFull(event, event.getBlock().getWorld(), event.getEntity().getType());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        World world = event.getEntity().getWorld();
        for (Entity transformed : event.getTransformedEntities()) {
            if (denyIfFull(event, world, transformed.getType())) {
                return;
            }
        }
    }

    private boolean denyIfFull(Cancellable event, World world, EntityType type) {
        if (limitHandler.getEntityLimit(type) == LimitHandler.UNLIMITED || !IslandUtils.isIslandWorld(world.getName())) {
            return false;
        }

        if (limitHandler.canSpawnEntity(world, type)) {
            return false;
        }

        event.setCancelled(true);
        plugin.debug("IslandLimitListener", "Denied entity due to limit: world=" + world.getName() + ", type=" + type);
        return true;
    }
}
