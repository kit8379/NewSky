package org.me.newsky.island.middleware;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.world.WorldHandler;

import java.util.Set;
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
    private final TeleportHandler teleportHandler;
    private final String serverID;

    public PostIslandHandler(NewSky plugin, CacheHandler cacheHandler, WorldHandler worldHandler, TeleportHandler teleportHandler, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUtils.UUIDToName(islandUuid);

        worldHandler.createWorld(islandName).thenRun(() -> {
            cacheHandler.updateIslandLoadedServer(islandUuid, serverID);
            future.complete(null);
            plugin.debug("createIsland completed successfully");
        });

        return future;
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUtils.UUIDToName(islandUuid);

        worldHandler.deleteWorld(islandName).thenRun(() -> {
            cacheHandler.removeIslandLoadedServer(islandUuid);
            future.complete(null);
            plugin.debug("deleteIsland completed successfully");
        });

        return future;
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUtils.UUIDToName(islandUuid);

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

        String islandName = IslandUtils.UUIDToName(islandUuid);

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

        String islandName = IslandUtils.UUIDToName(islandUuid);

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
                teleportHandler.addPendingTeleport(playerUuid, location);
                plugin.debug("Player " + playerUuid + " is not online, adding pending teleport to " + location);
            }

            future.complete(null);
            plugin.debug("teleportToIsland completed successfully");
        });

        return future;
    }

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        String islandName = IslandUtils.UUIDToName(islandUuid);
        World world = Bukkit.getWorld(islandName);
        if (world != null) {
            Set<UUID> islandPlayers = cacheHandler.getIslandPlayers(islandUuid);
            for (Player player : world.getPlayers()) {
                UUID playerUuid = player.getUniqueId();
                if (!islandPlayers.contains(playerUuid)) {
                    // Teleport player to default spawn that have configured in the plugin
                    plugin.debug("Removed player " + playerUuid + " from island " + islandName + " because the island is locked");
                }
            }
        }

        future.complete(null);
        plugin.debug("lockIsland completed successfully");

        return future;
    }
}