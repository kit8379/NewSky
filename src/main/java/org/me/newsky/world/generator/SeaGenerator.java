package org.me.newsky.world.generator;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class SeaGenerator extends ChunkGenerator {

    @Override
    @NotNull
    public ChunkData generateChunkData(@NotNull World world, @NotNull Random random, int x, int z, @NotNull BiomeGrid biome) {
        ChunkData chunk = createChunkData(world);

        // Fill the chunk with water up to y=63
        for (int y = 0; y < 64; y++) {
            for (int bx = 0; bx < 16; bx++) {
                for (int bz = 0; bz < 16; bz++) {
                    chunk.setBlock(bx, y, bz, Material.WATER);
                }
            }
        }

        // Set the spawn block at 0, 64, 0 (You can change this as needed)
        if (x == 0 && z == 0) {
            chunk.setBlock(0, 64, 0, Material.BEDROCK);
        }

        return chunk;
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return x == 0 && z == 0;
    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, 65, 0.5);
    }
}
