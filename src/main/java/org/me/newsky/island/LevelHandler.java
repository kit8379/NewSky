package org.me.newsky.island;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LevelHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public LevelHandler(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public CompletableFuture<Integer> getIslandLevel(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandLevel(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Map<UUID, Integer>> getTopIslandLevels(int size) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getTopIslandLevels(size), plugin.getBukkitAsyncExecutor());
    }

    public void calIslandLevel(UUID islandUuid) {
        CompletableFuture.runAsync(() -> {
            String islandName = IslandUtils.UUIDToName(islandUuid);

            Location center = LocationUtils.stringToLocation(islandName, config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch());

            int size = config.getIslandSize();
            List<Chunk> chunks = getChunksForIsland(center, size);

            int totalPoints = chunks.parallelStream().mapToInt(chunk -> {
                int points = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < chunk.getWorld().getMaxHeight(); y++) {
                            Block block = chunk.getBlock(x, y, z);
                            int value = config.getBlockLevel(block.getType().name());
                            points += value;
                        }
                    }
                }
                return points;
            }).sum();

            int totalLevel = (int) Math.round((double) totalPoints / 100);

            cacheHandler.updateIslandLevel(islandUuid, totalLevel);
        }, plugin.getBukkitAsyncExecutor());
    }

    private List<Chunk> getChunksForIsland(Location center, int size) {
        List<Chunk> chunks = new ArrayList<>();
        int halfSize = size / 2;
        int minX = (center.getBlockX() - halfSize) >> 4;
        int minZ = (center.getBlockZ() - halfSize) >> 4;
        int maxX = (center.getBlockX() + halfSize) >> 4;
        int maxZ = (center.getBlockZ() + halfSize) >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (center.getWorld().isChunkLoaded(x, z)) {
                    chunks.add(center.getWorld().getChunkAt(x, z));
                }
            }
        }
        return chunks;
    }
}
