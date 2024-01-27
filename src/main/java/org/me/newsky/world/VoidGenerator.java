package org.me.newsky.world;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import java.util.Random;
import org.jetbrains.annotations.NotNull;

public class VoidGenerator extends ChunkGenerator {

    @NotNull
    @Override
    public ChunkData generateChunkData(@NotNull World world, @NotNull Random random, int x, int z, @NotNull BiomeGrid biome) {
        ChunkData chunk = createChunkData(world);

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
