package org.me.newsky.island.operation;

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
public class LocalIslandOperation {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final String serverID;

    public LocalIslandOperation(NewSky plugin, CacheHandler cacheHandler, WorldHandler worldHandler, TeleportHandler teleportHandler, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid, String spawnLocation) {
        plugin.debug(getClass().getSimpleName(), "Creating island with UUID: " + islandUuid + " for owner: " + ownerUuid);
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.createWorld(islandName).thenRun(() -> {
            cacheHandler.createIsland(islandUuid);
            cacheHandler.updateIslandPlayer(islandUuid, ownerUuid, "owner");
            cacheHandler.updateHomePoint(islandUuid, ownerUuid, "default", spawnLocation);
            cacheHandler.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug(getClass().getSimpleName(), "Island " + islandName + " created successfully with owner " + ownerUuid);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Deleting island with UUID: " + islandUuid);
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.deleteWorld(islandName).thenRun(() -> {
            cacheHandler.deleteIsland(islandUuid);
            plugin.debug(getClass().getSimpleName(), "Island " + islandName + " deleted successfully");
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Loading island with UUID: " + islandUuid);
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.loadWorld(islandName).thenRun(() -> {
            cacheHandler.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug(getClass().getSimpleName(), "Island " + islandName + " loaded on server " + serverID);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Unloading island with UUID: " + islandUuid);
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.unloadWorld(islandName).thenRun(() -> {
            cacheHandler.removeIslandLoadedServer(islandUuid);
            plugin.debug(getClass().getSimpleName(), "Island " + islandName + " unloaded from server " + serverID);
        });
    }

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        plugin.debug(getClass().getSimpleName(), "Locking island with UUID: " + islandUuid);
        String islandName = IslandUtils.UUIDToName(islandUuid);

        World world = Bukkit.getWorld(islandName);
        if (world != null) {
            World safeWorld = Bukkit.getServer().getWorlds().getFirst();
            Set<UUID> islandPlayers = cacheHandler.getIslandPlayers(islandUuid);
            for (Player player : world.getPlayers()) {
                UUID playerUuid = player.getUniqueId();
                if (!islandPlayers.contains(playerUuid)) {
                    player.teleportAsync(safeWorld.getSpawnLocation());
                    plugin.debug(getClass().getSimpleName(), "Player " + playerUuid + " teleported to safe location due to island lock");
                }
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        plugin.debug(getClass().getSimpleName(), "Expelling player " + playerUuid + " from island " + islandUuid);
        String islandName = IslandUtils.UUIDToName(islandUuid);

        World world = Bukkit.getWorld(islandName);
        if (world != null) {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                plugin.debug(getClass().getSimpleName(), "Player " + playerUuid + " expelled from island " + islandName);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        plugin.debug(getClass().getSimpleName(), "Teleporting player " + playerUuid + " to island " + islandUuid + " at location: " + teleportLocation);
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return loadIsland(islandUuid).thenRun(() -> {
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
                plugin.debug(getClass().getSimpleName(), "Player " + playerUuid + " teleported to island " + islandUuid + " at location: " + teleportLocation);
            } else {
                teleportHandler.addPendingTeleport(playerUuid, location);
                plugin.debug(getClass().getSimpleName(), "Player " + playerUuid + " is offline, teleport will be processed when they log in");
            }
        });
    }
}
