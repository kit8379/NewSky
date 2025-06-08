// IslandLimitListener.java
package org.me.newsky.listener;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IslandLimitListener implements Listener {

    private final NewSky plugin;
    private final ConfigHandler config;

    private final Map<UUID, Map<Material, Integer>> islandBlockCounts = new HashMap<>();
    private final Map<UUID, Map<EntityType, Integer>> islandEntityCounts = new HashMap<>();

    public IslandLimitListener(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
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

        if (!canPlace(islandUuid, type)) {
            event.setCancelled(true);
            player.sendMessage(config.getBlockLimitMessage(type.name()));
            plugin.debug(getClass().getSimpleName(), "Block place cancelled for " + type.name() + " on island " + islandUuid);
            return;
        }

        registerBlockPlace(islandUuid, type);
    }


    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntitySpawn(CreatureSpawnEvent event) {
        String worldName = event.getLocation().getWorld().getName();

        if (!IslandUtils.isIslandWorld(worldName)) {
            plugin.debug(getClass().getSimpleName(), "Entity spawn in non-island world: " + worldName);
            return;
        }

        UUID islandUuid = IslandUtils.nameToUUID(worldName);
        EntityType type = event.getEntityType();

        if (!canSpawn(islandUuid, type)) {
            event.setCancelled(true);
            plugin.debug(getClass().getSimpleName(), "Entity spawn cancelled for " + type.name() + " on island " + islandUuid);
            return;
        }

        registerEntitySpawn(islandUuid, type);
    }

    private boolean canPlace(UUID islandUuid, Material type) {
        int current = islandBlockCounts.getOrDefault(islandUuid, new HashMap<>()).getOrDefault(type, 0);
        int limit = config.getBlockLimit(type.name());
        return limit == -1 || current < limit;
    }

    private boolean canSpawn(UUID islandUuid, EntityType type) {
        int current = islandEntityCounts.getOrDefault(islandUuid, new HashMap<>()).getOrDefault(type, 0);
        int limit = config.getEntityLimit(type.name());
        return limit == -1 || current < limit;
    }

    private void registerBlockPlace(UUID islandUuid, Material type) {
        islandBlockCounts.computeIfAbsent(islandUuid, id -> new HashMap<>()).merge(type, 1, Integer::sum);
    }

    private void registerEntitySpawn(UUID islandUuid, EntityType type) {
        islandEntityCounts.computeIfAbsent(islandUuid, id -> new HashMap<>()).merge(type, 1, Integer::sum);
    }
}
