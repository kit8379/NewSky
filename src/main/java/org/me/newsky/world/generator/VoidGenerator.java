package org.me.newsky.world.generator;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class VoidGenerator extends ChunkGenerator {

    private final int x;
    private final int y;
    private final int z;
    private final float yaw;
    private final float pitch;

    public VoidGenerator(int x, int y, int z, float yaw, float pitch) {
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
