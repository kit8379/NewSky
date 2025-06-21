package org.me.newsky.util;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class ChunkUtils {

    /**
     * Get all loaded chunks that are within the given island's area.
     *
     * @param center The center location of the island.
     * @param size   The size (width/length) of the island.
     * @return List of loaded chunks in the island area.
     */
    public static List<Chunk> getChunksForIsland(Location center, int size) {
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