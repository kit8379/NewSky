package org.me.newsky.island;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.ChunkUtils;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LevelHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final Cache cache;

    public LevelHandler(NewSky plugin, ConfigHandler config, Cache cache) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
    }

    public void calIslandLevel(UUID islandUuid) {
        CompletableFuture.runAsync(() -> {
            String islandName = IslandUtils.UUIDToName(islandUuid);

            Location center = LocationUtils.stringToLocation(islandName, config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch());

            int size = config.getIslandSize();
            List<Chunk> chunks = ChunkUtils.getChunksForIsland(center, size);

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

            cache.updateIslandLevel(islandUuid, totalLevel);
            plugin.debug("LevelHandler", "Calculated level for island " + islandUuid + ": " + totalLevel);
        }, plugin.getBukkitAsyncExecutor());
    }

    public int getIslandLevel(UUID islandUuid) {
        return cache.getIslandLevel(islandUuid);
    }

    public Map<UUID, Integer> getTopIslandLevels(int size) {
        return cache.getTopIslandLevels(size);
    }
}