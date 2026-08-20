package org.me.newsky.island;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.InvalidBiomeException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.exceptions.WorldNotFoundException;
import org.me.newsky.model.Actor;
import org.me.newsky.util.IslandUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class BiomeHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;

    public BiomeHandler(NewSky plugin, DatabaseHandler database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * SELF, scoped by island: a player may only re-biome the world of the island they belong to,
     * while a Bypass may re-biome any loaded world. One method for both callers - the actor is
     * what distinguishes them.
     */
    public CompletableFuture<Void> applyChunkBiome(Actor actor, String worldName, int chunkX, int chunkZ, String biomeName) {
        if (!(actor instanceof Actor.Player player)) {
            return applyBiome(worldName, chunkX, chunkZ, biomeName);
        }

        return CompletableFuture.runAsync(() -> {
            UUID islandUuid = database.getIslandUuid(player.uuid()).orElseThrow(IslandDoesNotExistException::new);

            if (!IslandUtils.UUIDToName(islandUuid).equals(worldName)) {
                throw new LocationNotInIslandException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> applyBiome(worldName, chunkX, chunkZ, biomeName));
    }

    private CompletableFuture<Void> applyBiome(String worldName, int chunkX, int chunkZ, String biomeName) {
        if (!Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(null).thenComposeAsync(v -> applyBiome(worldName, chunkX, chunkZ, biomeName), Bukkit.getScheduler().getMainThreadExecutor(plugin));
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
