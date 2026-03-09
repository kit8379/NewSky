package org.me.newsky.network.operator;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.RuntimeCache;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;
import org.me.newsky.world.WorldHandler;
import snapshot.IslandLoadedSnapshot;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandOperator {

    private final NewSky plugin;
    private final RuntimeCache runtimeCache;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final IslandLoadedSnapshot islandLoadedSnapshot;
    private final String serverID;

    public IslandOperator(NewSky plugin, RuntimeCache runtimeCache, WorldHandler worldHandler, TeleportHandler teleportHandler, IslandLoadedSnapshot islandLoadedSnapshot, String serverID) {
        this.plugin = plugin;
        this.runtimeCache = runtimeCache;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.islandLoadedSnapshot = islandLoadedSnapshot;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.createWorld(islandName).thenRun(() -> {
            runtimeCache.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug("IslandOperator", "Updated island loaded server for UUID: " + islandUuid + " on server: " + serverID);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.deleteWorld(islandName).thenRun(() -> {
            runtimeCache.removeIslandLoadedServer(islandUuid);
            plugin.debug("IslandOperator", "Removed island loaded server for UUID: " + islandUuid);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.loadWorld(islandName).thenRun(() -> {
            runtimeCache.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug("IslandOperator", "Updated island loaded server for UUID: " + islandUuid + " on server: " + serverID);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.unloadWorld(islandName).thenRun(() -> {
            runtimeCache.removeIslandLoadedServer(islandUuid);
            plugin.debug("IslandOperator", "Removed island loaded server for UUID: " + islandUuid);
        });
    }


    public CompletableFuture<Void> teleport(UUID playerUuid, String teleportWorld, String teleportLocation) {
        return CompletableFuture.runAsync(() -> {
            Location location = LocationUtils.stringToLocation(teleportWorld, teleportLocation);
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleportAsync(location);
            } else {
                teleportHandler.addPendingTeleport(playerUuid, location);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenRunAsync(() -> plugin.debug("IslandOperator", "Teleported player " + playerUuid + " to location: " + teleportLocation + " in world: " + teleportWorld), plugin.getBukkitAsyncExecutor());
    }


    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return plugin.getApi().getIslandPlayers(islandUuid).thenCompose(islandPlayers -> {
            return CompletableFuture.runAsync(() -> {
                World world = Bukkit.getWorld(islandName);
                if (world != null) {
                    for (Player player : world.getPlayers()) {
                        UUID playerUuid = player.getUniqueId();
                        if (!islandPlayers.contains(playerUuid)) {
                            player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                            plugin.getApi().lobby(playerUuid);
                        }
                    }
                }
            }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
        }).thenRunAsync(() -> plugin.debug("IslandOperator", "Locked island " + islandName + " and removed all foreign players."), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return CompletableFuture.runAsync(() -> {
            World world = Bukkit.getWorld(islandName);
            if (world != null) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.getWorld().equals(world)) {
                    player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                    plugin.getApi().lobby(playerUuid);
                }
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenRunAsync(() -> plugin.debug("IslandOperator", "Expelled player " + playerUuid + " from island " + islandName), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> updateIslandBorder(UUID islandUuid, int size) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return CompletableFuture.runAsync(() -> {
            World world = Bukkit.getWorld(islandName);
            if (world != null) {
                world.getWorldBorder().setSize(size);
                plugin.debug("IslandOperator", "Updated island border for " + islandName + " to size: " + size);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenRunAsync(() -> plugin.debug("IslandOperator", "Updated island border for island " + islandName + " to new size: " + size), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> reloadSnapshot(UUID islandUuid) {
        return islandLoadedSnapshot.reload(islandUuid);
    }
}
