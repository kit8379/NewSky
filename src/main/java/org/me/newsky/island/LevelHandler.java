package org.me.newsky.island;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
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
    private final Cache cache;

    public LevelHandler(NewSky plugin, ConfigHandler config, Cache cache) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
    }

    public void calIslandLevel(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);
        Location center = LocationUtils.stringToLocation(islandName, config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch());

        int size = config.getIslandSize();
        int halfSize = size / 2;

        int minX = (center.getBlockX() - halfSize) >> 4;
        int minZ = (center.getBlockZ() - halfSize) >> 4;
        int maxX = (center.getBlockX() + halfSize) >> 4;
        int maxZ = (center.getBlockZ() + halfSize) >> 4;

        World world = center.getWorld();

        List<CompletableFuture<ChunkSnapshot>> snapshotFutures = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                CompletableFuture<ChunkSnapshot> future = world.getChunkAtAsync(x, z, true).thenCompose(chunk -> {
                    if (chunk == null) {
                        return CompletableFuture.completedFuture(null);
                    }
                    CompletableFuture<ChunkSnapshot> snapshotFuture = new CompletableFuture<>();
                    plugin.getServer().getScheduler().runTask(plugin, () -> snapshotFuture.complete(chunk.getChunkSnapshot()));
                    return snapshotFuture;
                });
                snapshotFutures.add(future);
            }
        }

        CompletableFuture.allOf(snapshotFutures.toArray(new CompletableFuture[0])).thenApply(v -> snapshotFutures.stream().map(CompletableFuture::join).filter(snapshot -> snapshot != null).toList()).thenApplyAsync(snapshots -> {
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();

            return snapshots.parallelStream().mapToInt(snapshot -> {
                int points = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = minY; y < maxY; y++) {
                            String typeName = snapshot.getBlockType(x, y, z).name();
                            int value = config.getBlockLevel(typeName);
                            points += value;
                        }
                    }
                }
                return points;
            }).sum();
        }, plugin.getBukkitAsyncExecutor()).thenAccept(totalPoints -> {
            int totalLevel = (int) Math.round((double) totalPoints / 100);
            cache.updateIslandLevel(islandUuid, totalLevel);
            plugin.debug("LevelHandler", "Calculated level for island " + islandUuid + ": " + totalLevel);
        });
    }

    public int getIslandLevel(UUID islandUuid) {
        return cache.getIslandLevel(islandUuid);
    }

    public Map<UUID, Integer> getTopIslandLevels(int size) {
        return cache.getTopIslandLevels(size);
    }
}
