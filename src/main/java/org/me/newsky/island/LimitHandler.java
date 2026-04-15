package org.me.newsky.island;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LimitHandler {

    private final NewSky plugin;
    private final ConfigHandler config;

    private final ConcurrentHashMap<UUID, EnumMap<Material, Integer>> data = new ConcurrentHashMap<>();

    private volatile boolean[] trackedByMaterialOrdinal;

    public LimitHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
        startup();
    }

    public void startup() {
        Material[] materials = Material.values();
        boolean[] tracked = new boolean[materials.length];

        for (Material material : materials) {
            tracked[material.ordinal()] = config.getBlockLimit(material.name()) > 0;
        }

        this.trackedByMaterialOrdinal = tracked;
    }

    public CompletableFuture<Void> calIslandBlockLimit(UUID islandUuid) {
        if (!Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(null).thenComposeAsync(v -> calIslandBlockLimit(islandUuid), Bukkit.getScheduler().getMainThreadExecutor(plugin));
        }

        String islandName = IslandUtils.UUIDToName(islandUuid);
        World world = plugin.getServer().getWorld(islandName);

        if (world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("World not loaded for island: " + islandUuid + " (" + islandName + ")"));
        }

        int halfSize = config.getIslandSize() / 2;

        int minChunkX = Math.floorDiv(-halfSize, 16);
        int minChunkZ = Math.floorDiv(-halfSize, 16);
        int maxChunkX = Math.floorDiv(halfSize, 16);
        int maxChunkZ = Math.floorDiv(halfSize, 16);

        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunkFutures.add(world.getChunkAtAsync(cx, cz, true));
            }
        }

        CompletableFuture<Void> allLoaded = CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]));

        CompletableFuture<List<ChunkSnapshot>> snapshotsFuture = allLoaded.thenApply(unused -> {
            List<ChunkSnapshot> snapshots = new ArrayList<>(chunkFutures.size());

            for (CompletableFuture<Chunk> future : chunkFutures) {
                Chunk chunk = future.join();
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

            boolean[] trackedByOrdinal = this.trackedByMaterialOrdinal;
            return calculateSnapshotCounts(snapshots, minY, maxY, trackedByOrdinal);
        }, plugin.getBukkitAsyncExecutor()).thenAccept(counts -> {
            data.put(islandUuid, counts);
            plugin.debug("LimitHandler", "Rebuilt island block limits for " + islandUuid + ": " + counts);
        });
    }

    private EnumMap<Material, Integer> calculateSnapshotCounts(List<ChunkSnapshot> snapshots, int minY, int maxY, boolean[] trackedByOrdinal) {
        Material[] materials = Material.values();
        int[] countsByOrdinal = new int[materials.length];

        for (ChunkSnapshot snapshot : snapshots) {
            for (int y = minY; y < maxY; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Material material = snapshot.getBlockType(x, y, z);
                        int ordinal = material.ordinal();

                        if (trackedByOrdinal[ordinal]) {
                            countsByOrdinal[ordinal]++;
                        }
                    }
                }
            }
        }

        EnumMap<Material, Integer> result = new EnumMap<>(Material.class);

        for (int i = 0; i < countsByOrdinal.length; i++) {
            if (countsByOrdinal[i] > 0) {
                result.put(materials[i], countsByOrdinal[i]);
            }
        }

        return result;
    }

    public void unload(UUID islandUuid) {
        data.remove(islandUuid);
    }

    public boolean tryIncrement(UUID islandUuid, Material type) {
        if (islandUuid == null) {
            return false;
        }

        if (type == null) {
            return true;
        }

        EnumMap<Material, Integer> map = data.get(islandUuid);
        if (map == null) {
            return false;
        }

        int limit = config.getBlockLimit(type.name());

        synchronized (map) {
            if (limit <= 0) {
                map.remove(type);
                return true;
            }

            Integer current = map.get(type);

            if (current == null) {
                map.put(type, 1);
                return true;
            }

            if (current >= limit) {
                return false;
            }

            map.put(type, current + 1);
            return true;
        }
    }

    public void decrement(UUID islandUuid, Material type) {
        if (islandUuid == null || type == null) {
            return;
        }

        EnumMap<Material, Integer> map = data.get(islandUuid);
        if (map == null) {
            return;
        }

        synchronized (map) {
            Integer current = map.get(type);
            if (current == null) {
                return;
            }

            if (current <= 1) {
                map.remove(type);
            } else {
                map.put(type, current - 1);
            }
        }
    }

    public boolean canSpawnEntity(World world, EntityType entityType) {
        if (world == null || entityType == null) {
            return true;
        }

        int limit = config.getEntityLimit(entityType.name());
        if (limit <= 0) {
            return true;
        }

        int count = 0;

        for (Entity entity : world.getEntities()) {
            if (entity.getType() != entityType) {
                continue;
            }

            count++;

            if (count >= limit) {
                return false;
            }
        }

        return true;
    }
}