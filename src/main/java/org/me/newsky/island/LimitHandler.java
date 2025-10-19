package org.me.newsky.island;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.util.EnumMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LimitHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final ConcurrentHashMap<UUID, EnumMap<Material, Integer>> counts = new ConcurrentHashMap<>();

    public LimitHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    public int getLimit(Material material) {
        if (material == null) return 0;
        return config.getBlockLimit(material.name());
    }

    public void clear(UUID islandUuid) {
        counts.remove(islandUuid);
    }

    public boolean increment(UUID islandId, Material material) {
        int limit = getLimit(material);

        if (limit <= 0) {
            return true;
        }

        EnumMap<Material, Integer> map = counts.computeIfAbsent(islandId, k -> new EnumMap<>(Material.class));
        int before = map.getOrDefault(material, 0);
        int after = before + 1;

        if (after > limit) {
            return false;
        }

        map.put(material, after);

        return true;
    }

    public void decrement(UUID islandId, Material material) {
        if (getLimit(material) <= 0) {
            return;
        }

        EnumMap<Material, Integer> map = counts.computeIfAbsent(islandId, k -> new EnumMap<>(Material.class));
        int after = Math.max(0, map.getOrDefault(material, 0) - 1);

        if (after == 0) {
            map.remove(material);
            if (map.isEmpty()) counts.remove(islandId);
        } else {
            map.put(material, after);
        }
    }

    public int getLimit(EntityType type) {
        if (type == null) return 0;
        return config.getEntityLimit(type.name());
    }

    public boolean canSpawn(World world, EntityType type) {
        int limit = getLimit(type);
        if (limit <= 0) return true;
        long count = world.getEntities().stream().filter(e -> e.getType() == type).count();
        return count < limit;
    }
}
