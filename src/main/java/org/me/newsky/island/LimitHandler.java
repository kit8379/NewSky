package org.me.newsky.island;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-island block and entity caps from limits.yml. Block counts start from the material
 * histogram of the level scan (see {@link LevelHandler}) and are adjusted live on place and
 * break on the main thread; whatever else removes blocks is caught up by the next scan.
 * Entities are counted in the world at spawn time. Limit tables are built at startup and on
 * reload, so the event hot path never touches YAML; an unknown name fails startup.
 */
public class LimitHandler {

    /**
     * Limit of a material or entity type that limits.yml does not list.
     */
    public static final int UNLIMITED = -1;

    private final NewSky plugin;
    private final ConfigHandler config;

    private final ConcurrentHashMap<UUID, int[]> countsByIsland = new ConcurrentHashMap<>();

    private volatile int[] blockLimitByOrdinal;
    private volatile int[] entityLimitByOrdinal;

    public LimitHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
        startup();
    }

    public void startup() {
        int[] blockLimits = new int[Material.values().length];
        Arrays.fill(blockLimits, UNLIMITED);
        for (Map.Entry<String, Integer> limit : config.getBlockLimits().entrySet()) {
            Material material = Material.matchMaterial(limit.getKey());
            if (material == null) {
                throw new IllegalStateException("Block limits: '" + limit.getKey() + "' is not a material");
            }

            if (limit.getValue() < 0) {
                throw new IllegalStateException("Block limits: " + limit.getKey() + " must be 0 or more");
            }

            blockLimits[material.ordinal()] = limit.getValue();
        }

        int[] entityLimits = new int[EntityType.values().length];
        Arrays.fill(entityLimits, UNLIMITED);
        for (Map.Entry<String, Integer> limit : config.getEntityLimits().entrySet()) {
            EntityType type;
            try {
                type = EntityType.valueOf(limit.getKey());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Entity limits: '" + limit.getKey() + "' is not an entity type");
            }

            if (limit.getValue() < 0) {
                throw new IllegalStateException("Entity limits: " + limit.getKey() + " must be 0 or more");
            }

            entityLimits[type.ordinal()] = limit.getValue();
        }

        this.blockLimitByOrdinal = blockLimits;
        this.entityLimitByOrdinal = entityLimits;
        plugin.debug("LimitHandler", "Loaded block limits " + config.getBlockLimits() + " and entity limits " + config.getEntityLimits());
    }

    /**
     * Replaces an island's block counts with the histogram a scan just produced.
     */
    public void reset(UUID islandUuid, int[] countsByMaterialOrdinal) {
        countsByIsland.put(islandUuid, countsByMaterialOrdinal);
    }

    public void unload(UUID islandUuid) {
        countsByIsland.remove(islandUuid);
    }

    /**
     * The cap on that block per island: 0 forbids it outright, {@link #UNLIMITED} when not listed.
     */
    public int getBlockLimit(Material type) {
        return blockLimitByOrdinal[type.ordinal()];
    }

    /**
     * The cap on that entity per island world: 0 forbids it outright, {@link #UNLIMITED} when not listed.
     */
    public int getEntityLimit(EntityType type) {
        return entityLimitByOrdinal[type.ordinal()];
    }

    /**
     * Whether a scan has produced counts for the island yet.
     */
    public boolean isReady(UUID islandUuid) {
        return countsByIsland.containsKey(islandUuid);
    }

    /**
     * Counts one more of that block on the island if its cap allows. Denies until the first
     * scan has produced counts, so a freshly loaded island fails closed rather than open.
     */
    public boolean tryIncrement(UUID islandUuid, Material type) {
        int limit = blockLimitByOrdinal[type.ordinal()];
        if (limit == UNLIMITED) {
            return true;
        }

        int[] counts = countsByIsland.get(islandUuid);
        if (counts == null) {
            return false;
        }

        if (counts[type.ordinal()] >= limit) {
            return false;
        }

        counts[type.ordinal()]++;
        return true;
    }

    public void decrement(UUID islandUuid, Material type) {
        if (blockLimitByOrdinal[type.ordinal()] == UNLIMITED) {
            return;
        }

        int[] counts = countsByIsland.get(islandUuid);
        if (counts == null) {
            return;
        }

        if (counts[type.ordinal()] > 0) {
            counts[type.ordinal()]--;
        }
    }

    /**
     * Whether one more entity of that type fits in the world. Main thread: counts live entities.
     */
    public boolean canSpawnEntity(World world, EntityType type) {
        int limit = entityLimitByOrdinal[type.ordinal()];
        if (limit == UNLIMITED) {
            return true;
        }

        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity.getType() == type) {
                count++;
            }
        }

        return count < limit;
    }
}
