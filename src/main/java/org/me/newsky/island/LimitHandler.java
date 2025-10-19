package org.me.newsky.island;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
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

    public void calIslandBlockCount(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        int halfSize = config.getIslandSize() / 2;
        int minX = (-halfSize) >> 4;
        int minZ = (-halfSize) >> 4;
        int maxX = (halfSize) >> 4;
        int maxZ = (halfSize) >> 4;

        World world = plugin.getServer().getWorld(islandName);
        if (world == null) {
            plugin.debug("LimitHandler", "World not loaded for island " + islandName + ", skip seeding");
            return;
        }

        List<CompletableFuture<ChunkSnapshot>> snapshotFutures = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                CompletableFuture<ChunkSnapshot> future = world.getChunkAtAsync(x, z, true).thenCompose(chunk -> {
                    if (chunk == null) return CompletableFuture.completedFuture(null);
                    CompletableFuture<ChunkSnapshot> snapshotFuture = new CompletableFuture<>();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        try {
                            snapshotFuture.complete(chunk.getChunkSnapshot());
                        } catch (Exception ex) {
                            snapshotFuture.completeExceptionally(ex);
                        }
                    });
                    return snapshotFuture;
                });
                snapshotFutures.add(future);
            }
        }

        CompletableFuture.allOf(snapshotFutures.toArray(new CompletableFuture[0])).thenApply(v -> snapshotFutures.stream().map(CompletableFuture::join).filter(s -> s != null).toList()).thenApplyAsync(snapshots -> {
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();

            return snapshots.parallelStream().map(snapshot -> {
                EnumMap<Material, Integer> local = new EnumMap<>(Material.class);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            Material m = snapshot.getBlockType(x, y, z);
                            if (getLimit(m) <= 0) continue; // only limited materials
                            local.put(m, local.getOrDefault(m, 0) + 1);
                        }
                    }
                }
                return local;
            }).reduce(new EnumMap<>(Material.class), (a, b) -> {
                for (Map.Entry<Material, Integer> e : b.entrySet()) {
                    Material m = e.getKey();
                    a.put(m, a.getOrDefault(m, 0) + e.getValue());
                }
                return a;
            });
        }, plugin.getBukkitAsyncExecutor()).thenAccept(acc -> {
            if (acc.isEmpty()) return;
            EnumMap<Material, Integer> map = counts.computeIfAbsent(islandUuid, k -> new EnumMap<>(Material.class));
            for (Map.Entry<Material, Integer> e : acc.entrySet()) {
                Material m = e.getKey();
                int add = Math.max(0, e.getValue());
                map.put(m, map.getOrDefault(m, 0) + add);
            }
            if (map.isEmpty()) counts.remove(islandUuid);
            plugin.debug("LimitHandler", "Limit counts for " + islandUuid + ": " + acc);
        });
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
