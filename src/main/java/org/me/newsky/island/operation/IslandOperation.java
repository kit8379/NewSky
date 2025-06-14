package org.me.newsky.island.operation;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.cache.RedisCache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;
import org.me.newsky.world.WorldHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles post-island operations such as creating, deleting, loading, unloading, and locking islands.
 * This class is responsible for performing these operations on the server where the actual operation is to be performed.
 */
public class IslandOperation {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final Cache cache;
    private final RedisCache redisCache;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final String serverID;

    public IslandOperation(NewSky plugin, ConfigHandler config, Cache cache, RedisCache redisCache, WorldHandler worldHandler, TeleportHandler teleportHandler, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.cache = cache;
        this.redisCache = redisCache;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid, String spawnLocation) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.createWorld(islandName).thenRun(() -> {
            redisCache.updateIslandLoadedServer(islandUuid, serverID);
            cache.createIsland(islandUuid);
            cache.updateIslandPlayer(islandUuid, ownerUuid, "owner");
            cache.updateHomePoint(islandUuid, ownerUuid, "default", spawnLocation);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.deleteWorld(islandName).thenRun(() -> {
            redisCache.removeIslandLoadedServer(islandUuid);
            cache.deleteIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.loadWorld(islandName).thenRun(() -> redisCache.updateIslandLoadedServer(islandUuid, serverID));
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.unloadWorld(islandName).thenRun(() -> redisCache.removeIslandLoadedServer(islandUuid));
    }

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        World world = Bukkit.getWorld(islandName);
        if (world != null) {
            Set<UUID> islandPlayers = cache.getIslandPlayers(islandUuid);
            for (Player player : world.getPlayers()) {
                UUID playerUuid = player.getUniqueId();
                if (!islandPlayers.contains(playerUuid)) {
                    player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getLobbyCommand(player.getName())));
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return loadIsland(islandUuid).thenRun(() -> {
            Location location = LocationUtils.stringToLocation(islandName, teleportLocation);
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleportAsync(location);
            } else {
                teleportHandler.addPendingTeleport(playerUuid, location);
            }
        });
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        World world = Bukkit.getWorld(islandName);
        if (world != null) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getLobbyCommand(player.getName())));
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> sendPlayerMessage(UUID playerUuid, Component message) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage(message);
        }

        return CompletableFuture.completedFuture(null);
    }
}
