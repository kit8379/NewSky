package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.util.IslandUtils;

import java.util.*;
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

    public CompletableFuture<Integer> calIslandLevel(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> IslandUtils.UUIDToName(islandUuid), Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenCompose(islandName -> {
            int halfSize = config.getIslandSize() / 2;
            int minX = (-halfSize) >> 4;
            int minZ = (-halfSize) >> 4;
            int maxX = (halfSize) >> 4;
            int maxZ = (halfSize) >> 4;

            World world = plugin.getServer().getWorld(islandName);
            if (world == null) {
                int cachedLevel = getIslandLevel(islandUuid);
                plugin.debug("LevelHandler", "World not loaded for island " + islandName + ", returning cached level: " + cachedLevel);
                return CompletableFuture.completedFuture(cachedLevel);
            }

            List<CompletableFuture<ChunkSnapshot>> snapshots = new ArrayList<>();

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    CompletableFuture<ChunkSnapshot> snapshotFuture = world.getChunkAtAsync(x, z, true).thenCompose(chunk -> {
                        if (chunk == null) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return CompletableFuture.supplyAsync(chunk::getChunkSnapshot, Bukkit.getScheduler().getMainThreadExecutor(plugin));
                    });
                    snapshots.add(snapshotFuture);
                }
            }

            return CompletableFuture.allOf(snapshots.toArray(new CompletableFuture[0])).thenApply(v -> snapshots.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList()).thenApplyAsync(snapshotsList -> {
                int minY = world.getMinHeight();
                int maxY = world.getMaxHeight();

                return snapshotsList.parallelStream().mapToInt(snapshot -> {
                    int points = 0;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = minY; y < maxY; y++) {
                                String type = snapshot.getBlockType(x, y, z).name();
                                points += config.getBlockLevel(type);
                            }
                        }
                    }
                    return points;
                }).sum();
            }, plugin.getBukkitAsyncExecutor()).thenCompose(points -> CompletableFuture.supplyAsync(() -> {
                int totalLevel = (int) Math.round((double) points / 100);
                cache.updateIslandLevel(islandUuid, totalLevel);
                plugin.debug("LevelHandler", "Calculated level for island " + islandUuid + ": " + totalLevel);
                return totalLevel;
            }, Bukkit.getScheduler().getMainThreadExecutor(plugin)));
        });
    }

    public int getIslandLevel(UUID islandUuid) {
        return cache.getIslandLevel(islandUuid);
    }

    public Map<UUID, Integer> getTopIslandLevels(int size) {
        return cache.getTopIslandLevels(size);
    }
}
