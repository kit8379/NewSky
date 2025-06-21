package org.me.newsky.island;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.ChunkUtils;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class LimitHandler {

    private final NewSky plugin;
    private final ConfigHandler config;

    private final Map<UUID, Map<Material, Integer>> islandBlockCounts = new ConcurrentHashMap<>();

    public LimitHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    public CompletableFuture<Void> calIslandBlock(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> {
            String islandName = IslandUtils.UUIDToName(islandUuid);
            Location center = LocationUtils.stringToLocation(islandName, config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch());

            int size = config.getIslandSize();
            List<Chunk> chunks = ChunkUtils.getChunksForIsland(center, size);

            Map<Material, Integer> blockCounts = new HashMap<>();

            for (Chunk chunk : chunks) {
                int minY = chunk.getWorld().getMinHeight();
                int maxY = chunk.getWorld().getMaxHeight();

                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            Block block = chunk.getBlock(x, y, z);
                            Material type = block.getType();

                            int limit = config.getBlockLimit(type.name());
                            if (limit != -1) {
                                blockCounts.merge(type, 1, Integer::sum);
                            }
                        }
                    }
                }
            }

            islandBlockCounts.put(islandUuid, blockCounts);
            plugin.debug("LimitHandler", "Calculated block limits for island: " + islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public int getBlockCount(UUID islandUuid, Material material) {
        return islandBlockCounts.getOrDefault(islandUuid, Collections.emptyMap()).getOrDefault(material, 0);
    }

    public void incrementBlockCount(UUID islandUuid, Material material) {
        islandBlockCounts.computeIfAbsent(islandUuid, k -> new ConcurrentHashMap<>()).merge(material, 1, Integer::sum);
    }

    public void decrementBlockCount(UUID islandUuid, Material material) {
        islandBlockCounts.computeIfPresent(islandUuid, (id, map) -> {
            map.computeIfPresent(material, (mat, count) -> (count > 1) ? count - 1 : null);
            return map.isEmpty() ? null : map;
        });
    }
}
