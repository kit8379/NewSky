package org.me.newsky.island;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.me.newsky.NewSky;
import org.me.newsky.exceptions.InvalidBiomeException;
import org.me.newsky.exceptions.WorldNotFoundException;

import java.util.concurrent.CompletableFuture;

public final class BiomeHandler {

    private final NewSky plugin;

    public BiomeHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> applyChunkBiome(String worldName, int chunkX, int chunkZ, String biomeName) {
        if (!Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(null).thenComposeAsync(v -> applyChunkBiome(worldName, chunkX, chunkZ, biomeName), Bukkit.getScheduler().getMainThreadExecutor(plugin));
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return CompletableFuture.failedFuture(new WorldNotFoundException());
        }

        Biome biome = parseBiome(biomeName);
        if (biome == null) {
            return CompletableFuture.failedFuture(new InvalidBiomeException());
        }

        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int x = startX; x < startX + 16; x += 4) {
            for (int z = startZ; z < startZ + 16; z += 4) {
                for (int y = minY; y < maxY; y += 4) {
                    world.setBiome(x, y, z, biome);
                }
            }
        }

        world.refreshChunk(chunkX, chunkZ);
        return CompletableFuture.completedFuture(null);
    }

    private Biome parseBiome(String biomeName) {
        Registry<@org.jetbrains.annotations.NotNull Biome> biomeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);

        NamespacedKey key = NamespacedKey.fromString(biomeName);
        if (key == null) {
            key = NamespacedKey.minecraft(biomeName);
        }

        return biomeRegistry.get(key);
    }
}