package org.me.newsky.network;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.state.IslandServerState;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;
import org.me.newsky.world.WorldHandler;
import snapshot.IslandSnapshot;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class IslandOperator {

    private final NewSky plugin;
    private final DataCache dataCache;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final IslandSnapshot islandSnapshot;
    private final IslandServerState islandServerState;
    private final String serverID;

    public IslandOperator(NewSky plugin, DataCache dataCache, WorldHandler worldHandler, TeleportHandler teleportHandler, IslandSnapshot islandSnapshot, IslandServerState islandServerState, String serverID) {
        this.plugin = plugin;
        this.dataCache = dataCache;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.islandSnapshot = islandSnapshot;
        this.islandServerState = islandServerState;
        this.serverID = serverID;
    }

    // =====================================================================================
    // Request-response operations
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid, String homeLocation) {
        String islandName = IslandUtils.UUIDToName(islandUuid);
        AtomicBoolean databaseCreated = new AtomicBoolean(false);

        return CompletableFuture.runAsync(() -> {
            dataCache.createIsland(islandUuid, ownerUuid, homeLocation);
            databaseCreated.set(true);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandSnapshot.load(islandUuid);
        }).thenCompose(v -> {
            return worldHandler.createWorld(islandName);
        }).thenRun(() -> {
            islandServerState.updateIslandLoadedServer(islandUuid, serverID);
        }).thenCompose(v -> {
            return plugin.getApi().calIslandBlockLimit(islandUuid);
        }).thenRun(() -> {
            plugin.debug("IslandOperator", "Created island, updated loaded server, and calculated island limits for UUID: " + islandUuid + " on server: " + serverID);
        }).exceptionallyCompose(e -> {
            if (!databaseCreated.get()) {
                return CompletableFuture.failedFuture(e);
            }

            return cleanupFailedCreate(islandUuid, islandName).thenCompose(v -> CompletableFuture.failedFuture(e));
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return islandSnapshot.load(islandUuid).thenCompose(v -> {
            return worldHandler.loadWorld(islandName);
        }).thenRun(() -> {
            islandServerState.updateIslandLoadedServer(islandUuid, serverID);
        }).thenCompose(v -> {
            return plugin.getApi().calIslandBlockLimit(islandUuid);
        }).thenRun(() -> {
            plugin.debug("IslandOperator", "Loaded island, updated loaded server, and calculated island limits for UUID: " + islandUuid + " on server: " + serverID);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.unloadWorld(islandName).thenRun(() -> {
            islandServerState.removeIslandLoadedServer(islandUuid);
            islandSnapshot.unload(islandUuid);
            plugin.debug("IslandOperator", "Removed island loaded server for UUID: " + islandUuid);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);
        islandSnapshot.markDirty(islandUuid);

        return worldHandler.deleteWorld(islandName).thenRunAsync(() -> {
            dataCache.deleteIsland(islandUuid);
            islandServerState.removeIslandLoadedServer(islandUuid);
            islandSnapshot.unload(islandUuid);
            plugin.debug("IslandOperator", "Removed island loaded server for UUID: " + islandUuid);
        }, plugin.getBukkitAsyncExecutor());
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
    // Snapshot-backed operations
    // =====================================================================================

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role, String homeLocation) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.updateIslandPlayer(islandUuid, playerUuid, role, homeLocation));
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.deleteIslandPlayer(islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID oldOwnerUuid, UUID newOwnerUuid) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.updateIslandOwner(islandUuid, oldOwnerUuid, newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(UUID islandUuid, UUID playerUuid) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.updateBanPlayer(islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> removeBan(UUID islandUuid, UUID playerUuid) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.deleteBanPlayer(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, UUID playerUuid) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.updateCoopPlayer(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, UUID playerUuid) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.deleteCoopPlayer(islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> setIslandLock(UUID islandUuid, boolean locked) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.updateIslandLock(islandUuid, locked)).thenCompose(v -> {
            if (!locked) {
                return CompletableFuture.completedFuture(null);
            }

            String islandName = IslandUtils.UUIDToName(islandUuid);
            return plugin.getApi().getIslandPlayers(islandUuid).thenCompose(islandPlayers -> {
                return worldHandler.removePlayersFromWorld(islandName, player -> !islandPlayers.contains(player.getUniqueId()));
            });
        });
    }

    public CompletableFuture<Void> setIslandPvp(UUID islandUuid, boolean pvp) {
        return updateSnapshotBackedData(islandUuid, () -> dataCache.updateIslandPvp(islandUuid, pvp));
    }

    public CompletableFuture<Void> setUpgradeLevel(UUID islandUuid, String upgradeId, int level, int borderSize) {
        return updateSnapshotBackedData(islandUuid, () -> {
            dataCache.updateIslandUpgradeLevel(islandUuid, upgradeId, level);
        }).thenCompose(v -> borderSize > 0 ? updateBorder(islandUuid, borderSize) : CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<Void> updateBorder(UUID islandUuid, int size) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return CompletableFuture.runAsync(() -> {
            try {
                World world = Bukkit.getWorld(islandName);
                if (world != null) {
                    world.getWorldBorder().setSize(size);
                }

                plugin.debug("IslandOperator", "Updated border for " + islandName + " to size: " + size);
            } catch (Exception e) {
                plugin.severe("Failed to update border for island " + islandUuid + " to size " + size, e);
                throw new CompletionException(e);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private CompletableFuture<Void> updateSnapshotBackedData(UUID islandUuid, Runnable mutation) {
        islandSnapshot.markDirty(islandUuid);
        return CompletableFuture.runAsync(mutation, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandSnapshot.reload(islandUuid));
    }

    private CompletableFuture<Void> cleanupFailedCreate(UUID islandUuid, String islandName) {
        return worldHandler.deleteWorld(islandName).exceptionally(e -> {
            plugin.severe("Failed to cleanup world after island create failure: " + islandUuid, e);
            return null;
        }).thenRunAsync(() -> {
            try {
                dataCache.deleteIsland(islandUuid);
            } catch (Exception e) {
                plugin.severe("Failed to cleanup database after island create failure: " + islandUuid, e);
            }

            islandServerState.removeIslandLoadedServer(islandUuid);
            islandSnapshot.unload(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}
