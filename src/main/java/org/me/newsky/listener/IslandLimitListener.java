package org.me.newsky.listener;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;

public class IslandLimitListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final NewSkyAPI api;

    public IslandLimitListener(NewSky plugin, ConfigHandler config, NewSkyAPI api) {
        this.plugin = plugin;
        this.config = config;
        this.api = api;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        if (!IslandUtils.isIslandWorld(worldName)) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(worldName);
        Material type = event.getBlock().getType();

        int limit = config.getBlockLimit(type.name());
        int current = api.getBlockCount(islandUuid, type);

        if (limit != -1 && current >= limit) {
            event.setCancelled(true);
            player.sendMessage(config.getBlockLimitMessage(type.name()));
            plugin.debug("IslandLimitListener", "Block place cancelled for player " + player.getName() + " on island " + islandUuid + " for block type " + type.name() + ", limit reached.");
        } else {
            api.incrementBlockCount(islandUuid, type);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        String worldName = event.getBlock().getWorld().getName();

        if (!IslandUtils.isIslandWorld(worldName)) {
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(worldName);
        Material type = event.getBlock().getType();

        api.decrementBlockCount(islandUuid, type);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntitySpawn(CreatureSpawnEvent event) {
        String worldName = event.getLocation().getWorld().getName();

        if (!IslandUtils.isIslandWorld(worldName)) {
            return;
        }

        EntityType type = event.getEntityType();
        int limit = config.getEntityLimit(type.name());
        if (limit != -1) {
            int current = 0;
            for (Entity entity : event.getLocation().getWorld().getEntities()) {
                if (entity.getType() == type) {
                    current++;
                }
            }

            if (current >= limit) {
                event.setCancelled(true);
                plugin.debug("IslandLimitListener", "Entity spawn cancelled on island world " + worldName + " for entity type " + type.name() + ", limit reached.");
            }
        }
    }
}
