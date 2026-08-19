package org.me.newsky.network;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;
import org.me.newsky.world.WorldHandler;
import snapshot.IslandSnapshot;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class IslandOperator {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final IslandSnapshot islandSnapshot;
    private final IslandRegistry islandRegistry;
    private final String serverID;

    public IslandOperator(NewSky plugin, DatabaseHandler database, WorldHandler worldHandler, TeleportHandler teleportHandler, IslandSnapshot islandSnapshot, IslandRegistry islandRegistry, String serverID) {
        this.plugin = plugin;
        this.database = database;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.islandSnapshot = islandSnapshot;
        this.islandRegistry = islandRegistry;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid, String homeLocation) {
        String islandName = IslandUtils.UUIDToName(islandUuid);
        AtomicBoolean databaseCreated = new AtomicBoolean(false);

        return CompletableFuture.runAsync(() -> {
            database.addIslandData(islandUuid, ownerUuid, homeLocation);
            databaseCreated.set(true);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandSnapshot.load(islandUuid);
        }).thenCompose(v -> {
            return worldHandler.createWorld(islandName);
        }).thenRun(() -> {
            islandRegistry.updateIslandLoadedServer(islandUuid, serverID);
        }).thenRun(() -> {
            plugin.debug("IslandOperator", "Created island and updated loaded server for UUID: " + islandUuid + " on server: " + serverID);
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
            islandRegistry.updateIslandLoadedServer(islandUuid, serverID);
        }).thenRun(() -> {
            plugin.debug("IslandOperator", "Loaded island and updated loaded server for UUID: " + islandUuid + " on server: " + serverID);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.unloadWorld(islandName).thenRun(() -> {
            islandRegistry.removeIslandLoadedServer(islandUuid);
            islandSnapshot.unload(islandUuid);
            plugin.debug("IslandOperator", "Removed island loaded server for UUID: " + islandUuid);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.deleteWorld(islandName).thenRunAsync(() -> {
            database.deleteIsland(islandUuid);
            islandRegistry.removeIslandLoadedServer(islandUuid);
            islandSnapshot.unload(islandUuid);
            plugin.debug("IslandOperator", "Removed island loaded server for UUID: " + islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> prepareTeleport(UUID playerUuid, String teleportWorld, String teleportLocation) {
        return CompletableFuture.runAsync(() -> {
            Location location = LocationUtils.stringToLocation(teleportWorld, teleportLocation);
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleportAsync(location);
                return;
            }

            teleportHandler.addPendingTeleport(playerUuid, location);
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenRunAsync(() -> {
            plugin.debug("IslandOperator", "Teleported player " + playerUuid + " to location: " + teleportLocation + " in world: " + teleportWorld);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role, String homeLocation) {
        return updateSnapshot(islandUuid, () -> database.addIslandPlayer(islandUuid, playerUuid, role, homeLocation));
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.deleteIslandPlayer(islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID oldOwnerUuid, UUID newOwnerUuid) {
        return updateSnapshot(islandUuid, () -> database.updateIslandOwner(islandUuid, oldOwnerUuid, newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.updateBanPlayer(islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> removeBan(UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.deleteBanPlayer(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.updateCoopPlayer(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.deleteCoopPlayer(islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> setIslandLock(UUID islandUuid, boolean locked) {
        return updateSnapshot(islandUuid, () -> database.updateIslandLock(islandUuid, locked)).thenCompose(v -> {
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
        return updateSnapshot(islandUuid, () -> database.updateIslandPvp(islandUuid, pvp));
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        islandSnapshot.markDirty(islandUuid);

        return CompletableFuture.supplyAsync(() -> database.toggleIslandLock(islandUuid), plugin.getBukkitAsyncExecutor()).thenCompose(locked -> {
            if (!locked) {
                return CompletableFuture.completedFuture(locked);
            }

            String islandName = IslandUtils.UUIDToName(islandUuid);
            return plugin.getApi().getIslandPlayers(islandUuid).thenCompose(islandPlayers -> {
                return worldHandler.removePlayersFromWorld(islandName, player -> !islandPlayers.contains(player.getUniqueId()));
            }).thenApply(v -> locked);
        }).thenCompose(locked -> islandSnapshot.reload(islandUuid).thenApply(v -> locked));
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        islandSnapshot.markDirty(islandUuid);

        return CompletableFuture.supplyAsync(() -> database.toggleIslandPvp(islandUuid), plugin.getBukkitAsyncExecutor()).thenCompose(pvp -> islandSnapshot.reload(islandUuid).thenApply(v -> pvp));
    }

    private CompletableFuture<Void> updateSnapshot(UUID islandUuid, Runnable mutation) {
        islandSnapshot.markDirty(islandUuid);
        return CompletableFuture.runAsync(mutation, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandSnapshot.reload(islandUuid));
    }

    private CompletableFuture<Void> cleanupFailedCreate(UUID islandUuid, String islandName) {
        return worldHandler.deleteWorld(islandName).exceptionally(e -> {
            plugin.severe("Failed to cleanup world after island create failure: " + islandUuid, e);
            return null;
        }).thenRunAsync(() -> {
            try {
                database.deleteIsland(islandUuid);
            } catch (Exception e) {
                plugin.severe("Failed to cleanup database after island create failure: " + islandUuid, e);
            }

            islandRegistry.removeIslandLoadedServer(islandUuid);
            islandSnapshot.unload(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}
