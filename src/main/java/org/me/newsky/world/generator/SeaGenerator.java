package org.me.newsky.world.generator;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class SeaGenerator extends ChunkGenerator {

    private final int x;
    private final int y;
    private final int z;
    private final float yaw;
    private final float pitch;

    public SeaGenerator(int x, int y, int z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

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
        return chunk;
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return x == 0 && z == 0;
    }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
