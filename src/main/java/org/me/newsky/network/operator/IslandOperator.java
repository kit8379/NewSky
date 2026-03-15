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

    // =====================================================================================
    // Request-response operations
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return islandLoadedSnapshot.load(islandUuid).thenCompose(v -> worldHandler.createWorld(islandName)).thenRun(() -> {
            runtimeCache.updateIslandLoadedServer(islandUuid, serverID);
            plugin.debug("IslandOperator", "Updated island loaded server for UUID: " + islandUuid + " on server: " + serverID);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return islandLoadedSnapshot.load(islandUuid).thenCompose(v -> worldHandler.loadWorld(islandName)).thenRun(() -> {
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

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.deleteWorld(islandName).thenRun(() -> {
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
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenRunAsync(() -> {
            plugin.debug("IslandOperator", "Teleported player " + playerUuid + " to location: " + teleportLocation + " in world: " + teleportWorld);
        }, plugin.getBukkitAsyncExecutor());
    }

    // =====================================================================================
    // Fire-and-forget operations
    // =====================================================================================

    public void lockIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        plugin.getApi().getIslandPlayers(islandUuid).thenAccept(islandPlayers -> {
            Bukkit.getScheduler().getMainThreadExecutor(plugin).execute(() -> {
                try {
                    World world = Bukkit.getWorld(islandName);
                    if (world != null) {
                        Location fallback = Bukkit.getServer().getWorlds().getFirst().getSpawnLocation();

                        for (Player player : world.getPlayers()) {
                            UUID playerUuid = player.getUniqueId();
                            if (!islandPlayers.contains(playerUuid)) {
                                player.teleportAsync(fallback);
                                plugin.getApi().lobby(playerUuid);
                            }
                        }
                    }

                    plugin.debug("IslandOperator", "Locked island " + islandName + " and issued removal for all foreign players.");
                } catch (Exception e) {
                    plugin.severe("Failed to apply lock event for island " + islandUuid, e);
                }
            });
        }).exceptionally(ex -> {
            plugin.severe("Failed to fetch island players for lock event: " + islandUuid, ex);
            return null;
        });
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        Bukkit.getScheduler().getMainThreadExecutor(plugin).execute(() -> {
            try {
                World world = Bukkit.getWorld(islandName);
                if (world != null) {
                    Player player = Bukkit.getPlayer(playerUuid);
                    if (player != null && player.getWorld().equals(world)) {
                        player.teleportAsync(Bukkit.getServer().getWorlds().getFirst().getSpawnLocation());
                        plugin.getApi().lobby(playerUuid);
                    }
                }

                plugin.debug("IslandOperator", "Expelled player " + playerUuid + " from island " + islandName);
            } catch (Exception e) {
                plugin.severe("Failed to expel player " + playerUuid + " from island " + islandUuid, e);
            }
        });
    }

    public void updateBorder(UUID islandUuid, int size) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        Bukkit.getScheduler().getMainThreadExecutor(plugin).execute(() -> {
            try {
                World world = Bukkit.getWorld(islandName);
                if (world != null) {
                    world.getWorldBorder().setSize(size);
                }

                plugin.debug("IslandOperator", "Updated border for " + islandName + " to size: " + size);
            } catch (Exception e) {
                plugin.severe("Failed to update border for island " + islandUuid + " to size " + size, e);
            }
        });
    }

    public void reloadSnapshot(UUID islandUuid) {
        islandLoadedSnapshot.reload(islandUuid);
    }
}