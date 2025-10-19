package org.me.newsky.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.LimitHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandLimitListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final LimitHandler limits;

    public IslandLimitListener(NewSky plugin, ConfigHandler config, LimitHandler limits) {
        this.plugin = plugin;
        this.config = config;
        this.limits = limits;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        UUID islandUuid = IslandUtils.nameToUUID(event.getBlock().getWorld().getName());

        Material type = event.getBlockPlaced().getType();
        if (limits.getLimit(type) <= 0) {
            return;
        }

        if (!limits.increment(islandUuid, type)) {
            event.setCancelled(true);
            int limit = limits.getLimit(type);
            Component msg = config.getBlockLimitReachedMessage(type.name(), limit);
            event.getPlayer().sendMessage(msg);
            plugin.debug("IslandLimitListener", "Place blocked: " + type.name() + " on " + islandUuid + " limit=" + limit);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        UUID islandUuid = IslandUtils.nameToUUID(event.getBlock().getWorld().getName());

        Material type = event.getBlock().getType();
        if (limits.getLimit(type) <= 0) {
            return;
        }

        limits.decrement(islandUuid, type);
        plugin.debug("IslandLimitListener", "Break ok: " + type.name() + " on " + islandUuid + " decremented.");
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null) {
            return;
        }

        EntityType type = event.getEntityType();
        if (limits.getLimit(type) <= 0) {
            return;
        }

        if (!limits.canSpawn(world, type)) {
            event.setCancelled(true);
            plugin.debug("IslandLimitListener", "Mob spawn blocked: " + type.name() + " in world " + world.getName());
        }
    }
}
