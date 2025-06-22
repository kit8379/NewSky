package org.me.newsky.island.operation;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.redis.RedisCache;
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
    private final Cache cache;
    private final RedisCache redisCache;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final String serverID;

    public IslandOperation(NewSky plugin, Cache cache, RedisCache redisCache, WorldHandler worldHandler, TeleportHandler teleportHandler, String serverID) {
        this.plugin = plugin;
        this.cache = cache;
        this.redisCache = redisCache;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.createWorld(islandName).thenRun(() -> {
            redisCache.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug("IslandOperation", "Updated island loaded server for UUID: " + islandUuid + " on server: " + serverID);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.deleteWorld(islandName).thenRun(() -> {
            redisCache.removeIslandLoadedServer(islandUuid);
            plugin.debug("IslandOperation", "Removed island loaded server for UUID: " + islandUuid);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.loadWorld(islandName).thenRun(() -> {
            redisCache.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug("IslandOperation", "Updated island loaded server for UUID: " + islandUuid + " on server: " + serverID);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.unloadWorld(islandName).thenRun(() -> {
            redisCache.removeIslandLoadedServer(islandUuid);
            plugin.debug("IslandOperation", "Removed island loaded server for UUID: " + islandUuid);
        });
    }


    public CompletableFuture<Void> teleport(UUID playerUuid, String teleportWorld, String teleportLocation) {
        Location location = LocationUtils.stringToLocation(teleportWorld, teleportLocation);

        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleportAsync(location);
                future.complete(null);
            } else {
                teleportHandler.addPendingTeleport(playerUuid, location);
                future.complete(null);
            }
        });

        return future;
    }

    public void lockIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(islandName);
            if (world != null) {
                Set<UUID> islandPlayers = cache.getIslandPlayers(islandUuid);
                for (Player player : world.getPlayers()) {
                    UUID playerUuid = player.getUniqueId();
                    if (!islandPlayers.contains(playerUuid)) {
                        player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                        plugin.getApi().lobby(playerUuid);
                        plugin.debug("IslandOperation", "Teleported player " + player.getName() + " from locked island: " + world.getName());
                    }
                }
            }
        });
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        Bukkit.getScheduler().runTask(plugin, () -> {
            World world = Bukkit.getWorld(islandName);
            if (world != null) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.getWorld().equals(world)) {
                    player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                    plugin.getApi().lobby(playerUuid);
                    plugin.debug("IslandOperation", "Expelled player " + player.getName() + " from island: " + world.getName());
                }
            }
        });
    }

    public void sendMessage(UUID playerUuid, Component message) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(message);
                plugin.debug("IslandOperation", "Sent message to player " + player.getName() + ": " + message);
            }
        });
    }
}
