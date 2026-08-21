package org.me.newsky.island;

import org.bukkit.*;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.model.IslandTop;
import org.me.newsky.util.IslandUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LevelHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final DatabaseHandler database;

    private volatile int[] pointsByMaterialOrdinal;

    public LevelHandler(NewSky plugin, ConfigHandler config, DatabaseHandler database) {
        this.plugin = plugin;
        this.config = config;
        this.database = database;
        startup();
    }

    public void startup() {
        Material[] materials = Material.values();
        int[] table = new int[materials.length];

        for (Material material : materials) {
            table[material.ordinal()] = config.getBlockLevel(material.name());
        }

        this.pointsByMaterialOrdinal = table;
    }

    public CompletableFuture<Integer> recalculateIslandLevel(UUID islandUuid) {
        if (!Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(null).thenComposeAsync(v -> recalculateIslandLevel(islandUuid), Bukkit.getScheduler().getMainThreadExecutor(plugin));
        }

        String islandName = IslandUtils.UUIDToName(islandUuid);
        World world = plugin.getServer().getWorld(islandName);

        if (world == null) {
            return getIslandLevel(islandUuid).thenApply(cachedLevel -> {
                plugin.debug("LevelHandler", "World not loaded for island " + islandName + ", returning cached level: " + cachedLevel);
                return cachedLevel;
            });
        }

        // The island's build area is [-half, half-1] on both axes - the same bounds the
        // protection listener enforces. Chunks are scanned only as far as that area reaches,
        // and each chunk is clipped to it below, so blocks beyond the border never score.
        int halfSize = config.getIslandSize() / 2;

        int minChunkX = Math.floorDiv(-halfSize, 16);
        int minChunkZ = Math.floorDiv(-halfSize, 16);
        int maxChunkX = Math.floorDiv(halfSize - 1, 16);
        int maxChunkZ = Math.floorDiv(halfSize - 1, 16);

        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunkFutures.add(world.getChunkAtAsync(cx, cz, true));
            }
        }

        CompletableFuture<Void> allLoaded = CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]));

        CompletableFuture<List<ChunkSnapshot>> snapshotsFuture = allLoaded.thenApply(chunks -> {
            List<ChunkSnapshot> snapshots = new ArrayList<>(chunkFutures.size());

            for (CompletableFuture<Chunk> f : chunkFutures) {
                Chunk chunk = f.join();
                if (chunk == null) {
                    continue;
                }

                snapshots.add(chunk.getChunkSnapshot());
            }

            return snapshots;
        });

        return snapshotsFuture.thenApplyAsync(snapshots -> {
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            int[] table = this.pointsByMaterialOrdinal;

            long totalPoints = 0;

            for (ChunkSnapshot snapshot : snapshots) {
                totalPoints += calculateSnapshotPoints(snapshot, minY, maxY, halfSize, table);
            }

            return (int) Math.round((double) totalPoints / 100.0);
        }, plugin.getBukkitAsyncExecutor()).thenApply(totalLevel -> {
            database.setIslandLevel(islandUuid, totalLevel);
            plugin.debug("LevelHandler", "Calculated level for island " + islandUuid + ": " + totalLevel);
            return totalLevel;
        });
    }

    private static long calculateSnapshotPoints(ChunkSnapshot snapshot, int minY, int maxY, int halfSize, int[] table) {
        // Clip the chunk's local coordinates to the island's build area so columns outside the
        // border (edge chunks straddle it) contribute nothing.
        int chunkBaseX = snapshot.getX() << 4;
        int chunkBaseZ = snapshot.getZ() << 4;

        int startX = Math.max(0, -halfSize - chunkBaseX);
        int endX = Math.min(15, (halfSize - 1) - chunkBaseX);
        int startZ = Math.max(0, -halfSize - chunkBaseZ);
        int endZ = Math.min(15, (halfSize - 1) - chunkBaseZ);

        if (startX > endX || startZ > endZ) {
            return 0;
        }

        long points = 0;

        for (int y = minY; y < maxY; y++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int x = startX; x <= endX; x++) {

                    Material mat = snapshot.getBlockType(x, y, z);
                    points += table[mat.ordinal()];

                }
            }
        }

        return points;
    }

    public CompletableFuture<Integer> getIslandLevel(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return database.getIslandCore(islandUuid).map(DatabaseHandler.IslandCoreData::level).orElse(0);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<List<IslandTop>> getTopIslandLevels(int limit) {
        return CompletableFuture.supplyAsync(() -> database.getTopIslandLevels(limit), plugin.getBukkitAsyncExecutor());
    }
}
