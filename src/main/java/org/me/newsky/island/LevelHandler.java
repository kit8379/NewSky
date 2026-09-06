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

    private static final int SCAN_BATCH_SIZE = 16;

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

    public CompletableFuture<Integer> calIslandLevel(UUID islandUuid) {
        if (!Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(null).thenComposeAsync(v -> calIslandLevel(islandUuid), Bukkit.getScheduler().getMainThreadExecutor(plugin));
        }

        String islandName = IslandUtils.UUIDToName(islandUuid);
        World world = plugin.getServer().getWorld(islandName);

        if (world == null) {
            return getIslandLevel(islandUuid).thenApply(cachedLevel -> {
                plugin.debug("LevelHandler", "World not loaded for island " + islandName + ", returning cached level: " + cachedLevel);
                return cachedLevel;
            });
        }

        int halfSize = config.getIslandSize() / 2;

        int minChunkX = Math.floorDiv(-halfSize, 16);
        int minChunkZ = Math.floorDiv(-halfSize, 16);
        int maxChunkX = Math.floorDiv(halfSize, 16);
        int maxChunkZ = Math.floorDiv(halfSize, 16);

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        List<int[]> chunkCoords = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunkCoords.add(new int[]{cx, cz});
            }
        }

        // Scan in small sequential batches so the snapshot work never hits the main thread
        // as one full-island burst, and only a batch worth of chunks is loaded at a time.
        CompletableFuture<Long> totalPoints = CompletableFuture.completedFuture(0L);
        for (int start = 0; start < chunkCoords.size(); start += SCAN_BATCH_SIZE) {
            List<int[]> batch = chunkCoords.subList(start, Math.min(start + SCAN_BATCH_SIZE, chunkCoords.size()));
            totalPoints = totalPoints.thenCompose(acc -> scanBatch(world, batch, minY, maxY).thenApply(points -> acc + points));
        }

        return totalPoints.thenApplyAsync(total -> {
            int totalLevel = (int) Math.round((double) total / 100.0);
            database.updateIslandLevel(islandUuid, totalLevel);
            plugin.debug("LevelHandler", "Calculated level for island " + islandUuid + ": " + totalLevel);
            return totalLevel;
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Long> scanBatch(World world, List<int[]> batch, int minY, int maxY) {
        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>(batch.size());
        for (int[] coord : batch) {
            // gen=false: a never-generated chunk holds nothing and scores zero, so skip it
            // instead of generating and persisting it just to scan.
            chunkFutures.add(world.getChunkAtAsync(coord[0], coord[1], false));
        }

        return CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0])).thenApplyAsync(v -> {
            List<ChunkSnapshot> snapshots = new ArrayList<>(chunkFutures.size());

            for (CompletableFuture<Chunk> f : chunkFutures) {
                Chunk chunk = f.join();
                if (chunk == null) {
                    continue;
                }

                snapshots.add(chunk.getChunkSnapshot());
            }

            return snapshots;
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenApplyAsync(snapshots -> {
            int[] table = this.pointsByMaterialOrdinal;

            long points = 0;
            for (ChunkSnapshot snapshot : snapshots) {
                points += calculateSnapshotPoints(snapshot, minY, maxY, table);
            }

            return points;
        }, plugin.getBukkitAsyncExecutor());
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

    public CompletableFuture<Integer> getIslandLevel(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return database.getIslandLevel(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<List<IslandTop>> getTopIslandLevels(int limit) {
        return CompletableFuture.supplyAsync(() -> database.getTopIslandLevels(limit), plugin.getBukkitAsyncExecutor());
    }
}
