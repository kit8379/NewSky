package org.me.newsky.listener;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.LimitHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

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
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlockPlaced().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material type = event.getBlockPlaced().getType();
        int limit = config.getBlockLimit(type.name());

        if (limit <= 0) {
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
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material type = event.getBlock().getType();
        int limit = config.getBlockLimit(type.name());

        if (limit <= 0) {
            return;
        }

        limitHandler.decrement(islandUuid, type);

        plugin.debug("IslandLimitListener", "Decremented block limit count after break: island=" + islandUuid + ", type=" + type + ", player=" + event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        UUID islandUuid = getIslandUuidIfIslandWorld(event.getBlock().getWorld());
        if (islandUuid == null) {
            return;
        }

        Material type = event.getBlock().getType();
        int limit = config.getBlockLimit(type.name());

        if (limit <= 0) {
            return;
        }

        limitHandler.decrement(islandUuid, type);

        plugin.debug("IslandLimitListener", "Decremented block limit count after burn: island=" + islandUuid + ", type=" + type + ", location=" + event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        UUID islandUuid = getIslandUuidIfIslandWorld(world);

        if (islandUuid == null) {
            return;
        }

        EntityType type = event.getEntityType();

        if (!limitHandler.canSpawnEntity(world, type)) {
            event.setCancelled(true);
            plugin.debug("IslandLimitListener", "Denied creature spawn due to entity limit: island=" + islandUuid + ", type=" + type + ", reason=" + event.getSpawnReason() + ", location=" + event.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        World world = event.getEntity().getWorld();
        UUID islandUuid = getIslandUuidIfIslandWorld(world);

        if (islandUuid == null) {
            return;
        }

        for (Entity transformed : event.getTransformedEntities()) {
            EntityType type = transformed.getType();

            if (!limitHandler.canSpawnEntity(world, type)) {
                event.setCancelled(true);

                plugin.debug("IslandLimitListener", "Denied entity transform due to entity limit: island=" + islandUuid + ", fromType=" + event.getEntity().getType() + ", toType=" + type + ", reason=" + event.getTransformReason() + ", location=" + event.getEntity().getLocation());
                return;
            }
        }
    }

    private UUID getIslandUuidIfIslandWorld(World world) {
        if (world == null) {
            return null;
        }

        String worldName = world.getName();
        if (!IslandUtils.isIslandWorld(worldName)) {
            return null;
        }

        return IslandUtils.nameToUUID(worldName);
    }
}