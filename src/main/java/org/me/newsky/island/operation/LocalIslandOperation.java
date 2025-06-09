package org.me.newsky.island.operation;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
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
public class LocalIslandOperation {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final String serverID;

    public LocalIslandOperation(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, WorldHandler worldHandler, TeleportHandler teleportHandler, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid, String spawnLocation) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.createWorld(islandName).thenRun(() -> {
            cacheHandler.updateIslandLoadedServer(islandUuid, serverID);
            cacheHandler.createIsland(islandUuid);
            cacheHandler.updateIslandPlayer(islandUuid, ownerUuid, "owner");
            cacheHandler.updateHomePoint(islandUuid, ownerUuid, "default", spawnLocation);
            plugin.debug(getClass().getSimpleName(), "Island " + islandName + " created successfully with owner " + ownerUuid);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.deleteWorld(islandName).thenRun(() -> {
            cacheHandler.removeIslandLoadedServer(islandUuid);
            cacheHandler.deleteIsland(islandUuid);
            plugin.debug(getClass().getSimpleName(), "Island " + islandName + " deleted successfully");
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.loadWorld(islandName).thenRun(() -> {
            cacheHandler.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug(getClass().getSimpleName(), "Island " + islandName + " loaded on server " + serverID);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.unloadWorld(islandName).thenRun(() -> {
            cacheHandler.removeIslandLoadedServer(islandUuid);
            plugin.debug(getClass().getSimpleName(), "Island " + islandName + " unloaded from server " + serverID);
        });
    }

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        World world = Bukkit.getWorld(islandName);
        if (world != null) {
            Set<UUID> islandPlayers = cacheHandler.getIslandPlayers(islandUuid);
            for (Player player : world.getPlayers()) {
                UUID playerUuid = player.getUniqueId();
                if (!islandPlayers.contains(playerUuid)) {
                    player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getLobbyCommand(player.getName())));
                    plugin.debug(getClass().getSimpleName(), "Player " + playerUuid + " teleported to safe location due to island lock");
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
                plugin.debug(getClass().getSimpleName(), "Player " + playerUuid + " teleported to island " + islandUuid + " at location: " + teleportLocation);
            } else {
                teleportHandler.addPendingTeleport(playerUuid, location);
                plugin.debug(getClass().getSimpleName(), "Player " + playerUuid + " is offline, teleport will be processed when they log in");
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
                plugin.debug(getClass().getSimpleName(), "Player " + playerUuid + " expelled from island " + islandName);
            }
        }

        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> sendPlayerMessage(UUID playerUuid, Component message) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage(message);
            plugin.debug(getClass().getSimpleName(), "Sent message to player " + playerUuid);
        }

        return CompletableFuture.completedFuture(null);
    }
}
