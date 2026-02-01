package org.me.newsky.island;

import org.bukkit.*;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LevelHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final Cache cache;


    private volatile int[] pointsByMaterialOrdinal;

    public LevelHandler(NewSky plugin, ConfigHandler config, Cache cache) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
        reload();
    }

    public void reload() {
        Material[] materials = Material.values();
        int[] table = new int[materials.length];

        for (Material material : materials) {
            table[material.ordinal()] = config.getBlockLevel(material.name());
        }

        this.pointsByMaterialOrdinal = table;
    }

    public CompletableFuture<Integer> calIslandLevel(UUID islandUuid) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("calIslandLevel must be called from the main server thread");
        }

        String islandName = IslandUtils.UUIDToName(islandUuid);

        int halfSize = config.getIslandSize() / 2;

        // Correct chunk bounds (floor) for negative values too.
        int minChunkX = Math.floorDiv(-halfSize, 16);
        int minChunkZ = Math.floorDiv(-halfSize, 16);
        int maxChunkX = Math.floorDiv(halfSize, 16);
        int maxChunkZ = Math.floorDiv(halfSize, 16);

        World world = plugin.getServer().getWorld(islandName);
        if (world == null) {
            int cachedLevel = getIslandLevel(islandUuid);
            plugin.debug("LevelHandler", "World not loaded for island " + islandName + ", returning cached level: " + cachedLevel);
            return CompletableFuture.completedFuture(cachedLevel);
        }

        // 1) Load all chunks asynchronously (Paper API)
        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunkFutures.add(world.getChunkAtAsync(cx, cz, true));
            }
        }

        CompletableFuture<Void> allLoaded = CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]));

        // 2) Capture all snapshots on the main thread in ONE task (avoid per-chunk scheduling overhead)
        CompletableFuture<List<ChunkSnapshot>> snapshotsFuture = allLoaded.thenCompose(v -> {
            CompletableFuture<List<ChunkSnapshot>> result = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    List<ChunkSnapshot> snapshots = new ArrayList<>(chunkFutures.size());
                    for (CompletableFuture<Chunk> f : chunkFutures) {
                        Chunk chunk = f.join();
                        if (chunk == null) continue;
                        snapshots.add(chunk.getChunkSnapshot());
                    }
                    result.complete(snapshots);
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            });
            return result;
        });

        // 3) Heavy scan off-thread using fast lookup table
        return snapshotsFuture.thenApplyAsync(snapshots -> {
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            int[] table = this.pointsByMaterialOrdinal;

            long totalPoints = 0;
            for (ChunkSnapshot snapshot : snapshots) {
                totalPoints += calculateSnapshotPoints(snapshot, minY, maxY, table);
            }

            return (int) Math.round((double) totalPoints / 100.0);
        }, plugin.getBukkitAsyncExecutor()).thenApply(totalLevel -> {
            cache.updateIslandLevel(islandUuid, totalLevel);
            plugin.debug("LevelHandler", "Calculated level for island " + islandUuid + ": " + totalLevel);
            return totalLevel;
        });
    }

    private static long calculateSnapshotPoints(ChunkSnapshot snapshot, int minY, int maxY, int[] table) {
        long points = 0;

        for (int y = minY; y < maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Material mat = snapshot.getBlockType(x, y, z);
                    points += table[mat.ordinal()];
                }
            }
        }

        return points;
    }

    public int getIslandLevel(UUID islandUuid) {
        return cache.getIslandLevel(islandUuid);
    }

    public Map<UUID, Integer> getTopIslandLevels(int size) {
        return cache.getTopIslandLevels(size);
    }
}
