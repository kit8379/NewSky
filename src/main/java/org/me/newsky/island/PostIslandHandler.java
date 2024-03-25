package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.event.*;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.util.IslandUUIDUtils;
import org.me.newsky.world.WorldHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles post-island operations such as creating, deleting, loading, unloading, and locking islands.
 * This class is responsible for performing these operations on the server where the actual operation is to be performed.
 */
public class PostIslandHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final WorldHandler worldHandler;
    private final TeleportManager teleportManager;
    private final String serverID;

    public PostIslandHandler(NewSky plugin, CacheHandler cacheHandler, WorldHandler worldHandler, TeleportManager teleportManager, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.worldHandler = worldHandler;
        this.teleportManager = teleportManager;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID playerUuid, String spawnLocation) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.createWorld(islandName).thenRun(() -> {
            cacheHandler.createIsland(islandUuid);
            cacheHandler.updateIslandPlayer(islandUuid, playerUuid, "owner");
            cacheHandler.updateHomePoint(islandUuid, playerUuid, "default", spawnLocation);
            cacheHandler.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug("Created island " + islandUuid + " in the cache");
            future.complete(null);
            plugin.debug("createIsland completed successfully");
        });

        return future;
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.deleteWorld(islandName).thenRun(() -> {
            cacheHandler.deleteIsland(islandUuid);
            cacheHandler.removeIslandLoadedServer(islandUuid);
            plugin.debug("Deleted island " + islandName + " from the cache");
            future.complete(null);
            plugin.debug("deleteIsland completed successfully");
        });

        return future;
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.loadWorld(islandName).thenRun(() -> {
            cacheHandler.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug("Loaded island " + islandName);
            future.complete(null);
            plugin.debug("loadIsland completed successfully");
        });

        return future;
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();


        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        worldHandler.unloadWorld(islandName).thenRun(() -> {
            cacheHandler.removeIslandLoadedServer(islandUuid);
            plugin.debug("Unloaded island " + islandName);
            future.complete(null);
            plugin.debug("unloadIsland completed successfully");
        });

        return future;
    }

    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);

        loadIsland(islandUuid).thenRun(() -> {
            String[] parts = teleportLocation.split(",");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = Float.parseFloat(parts[3]);
            float pitch = Float.parseFloat(parts[4]);

            Location location = new Location(Bukkit.getWorld(islandName), x, y, z, yaw, pitch);
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleportAsync(location);
                plugin.debug("Player " + playerUuid + " is online, teleporting to " + location);
            } else {
                teleportManager.addPendingTeleport(playerUuid, location);
                plugin.debug("Player " + playerUuid + " is not online, adding pending teleport to " + location);
            }

            future.complete(null);
            plugin.debug("teleportToIsland completed successfully");
        });

        return future;
    }

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUUIDUtils.UUIDToName(islandUuid);
        worldHandler.lockWorld(islandName);

        future.complete(null);
        plugin.debug("lockIsland completed successfully");

        return future;
    }
}
