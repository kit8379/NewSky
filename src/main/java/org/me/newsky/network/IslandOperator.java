package org.me.newsky.network;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.CannotExpelIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.model.Actor;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;
import org.me.newsky.world.WorldHandler;
import snapshot.IslandSnapshot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class IslandOperator {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final IslandSnapshot islandSnapshot;
    private final IslandRegistry islandRegistry;
    private final String serverID;

    private final Map<UUID, CompletableFuture<Void>> loadsInFlight = new ConcurrentHashMap<>();

    public IslandOperator(NewSky plugin, DatabaseHandler database, WorldHandler worldHandler, TeleportHandler teleportHandler, IslandSnapshot islandSnapshot, IslandRegistry islandRegistry, String serverID) {
        this.plugin = plugin;
        this.database = database;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.islandSnapshot = islandSnapshot;
        this.islandRegistry = islandRegistry;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);
        AtomicBoolean databaseCreated = new AtomicBoolean(false);

        return CompletableFuture.runAsync(() -> {
            database.addIslandData(islandUuid, ownerUuid);
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

    /**
     * Loads an island, collapsing concurrent requests for the same island into one load. Two
     * requests reaching this server at once must not both run {@code loadWorld}, and every caller
     * has to keep waiting until the world is actually available.
     */
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> inFlight = loadsInFlight.putIfAbsent(islandUuid, gate);
        if (inFlight != null) {
            return inFlight;
        }

        doLoadIsland(islandUuid).whenComplete((result, error) -> {
            loadsInFlight.remove(islandUuid, gate);

            if (error != null) {
                gate.completeExceptionally(error);
            } else {
                gate.complete(null);
            }
        });

        return gate;
    }

    private CompletableFuture<Void> doLoadIsland(UUID islandUuid) {
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

    public CompletableFuture<Void> deleteIsland(Actor actor, UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        // Rows go first: the ownership check and the delete share one transaction, so a refused
        // delete changes nothing. If removing the world fails afterwards, all that remains is an
        // orphaned world file no island row points at - unreachable garbage rather than a live bug.
        return CompletableFuture.runAsync(() -> database.deleteIsland(actor, islandUuid), plugin.getBukkitAsyncExecutor()).thenCompose(v -> worldHandler.deleteWorld(islandName).exceptionally(e -> {
            plugin.severe("Island rows deleted but the world could not be removed, leaving an orphaned world: " + islandName, e);
            return null;
        })).thenRun(() -> {
            islandRegistry.removeIslandLoadedServer(islandUuid);
            islandSnapshot.unload(islandUuid);
            plugin.debug("IslandOperator", "Deleted island and released loaded server for UUID: " + islandUuid);
        });
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

    /**
     * Kicks a visitor out of the island world. The membership guard runs here - on the island's
     * host server, at the moment of the kick - not only at the caller: the caller's check and this
     * kick are separated by a cross-server hop, and the target may have become a member in between.
     * Guarding at the point of effect shrinks that window to the gap between a membership commit
     * and this read. A member slipping through even that is only bounced to the lobby once.
     */
    public CompletableFuture<Void> expelPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).keySet(),
                plugin.getBukkitAsyncExecutor()).thenCompose(islandPlayers -> {
            if (actor instanceof Actor.Player player && !islandPlayers.contains(player.uuid())) {
                throw new IslandDoesNotExistException();
            }

            if (islandPlayers.contains(playerUuid)) {
                throw new CannotExpelIslandPlayerException();
            }

            return worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid);
        });
    }

    public CompletableFuture<Void> refreshSnapshot(UUID islandUuid) {
        return islandSnapshot.reload(islandUuid);
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return updateSnapshot(islandUuid, () -> database.addIslandPlayer(islandUuid, playerUuid, role));
    }

    public CompletableFuture<Void> removeMember(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.deleteIslandPlayer(actor, islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> setOwner(Actor actor, UUID islandUuid, UUID newOwnerUuid) {
        return updateSnapshot(islandUuid, () -> database.updateIslandOwner(actor, islandUuid, newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.updateBanPlayer(actor, islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> removeBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.deleteBanPlayer(actor, islandUuid, playerUuid));
    }

    public CompletableFuture<Void> addCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.updateCoopPlayer(actor, islandUuid, playerUuid));
    }

    public CompletableFuture<Void> removeCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> database.deleteCoopPlayer(actor, islandUuid, playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Boolean> toggleIslandLock(Actor actor, UUID islandUuid) {
        return updateSnapshotAndGet(islandUuid, () -> database.toggleIslandLock(actor, islandUuid)).thenCompose(locked -> {
            if (!locked) {
                return CompletableFuture.completedFuture(false);
            }

            return removeNonMembersFromWorld(islandUuid).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> toggleIslandPvp(Actor actor, UUID islandUuid) {
        return updateSnapshotAndGet(islandUuid, () -> database.toggleIslandPvp(actor, islandUuid));
    }

    private CompletableFuture<Void> removeNonMembersFromWorld(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return plugin.getApi().getIslandPlayers(islandUuid).thenCompose(islandPlayers -> worldHandler.removePlayersFromWorld(islandName, player -> !islandPlayers.contains(player.getUniqueId())));
    }

    private CompletableFuture<Void> updateSnapshot(UUID islandUuid, Runnable mutation) {
        return updateSnapshotAndGet(islandUuid, () -> {
            mutation.run();
            return null;
        });
    }

    private <T> CompletableFuture<T> updateSnapshotAndGet(UUID islandUuid, Supplier<T> mutation) {
        // The snapshot is refreshed even when the mutation fails, because a mutation can fail after
        // having written something (or after another server wrote), and skipping the reload would
        // leave this server serving a snapshot it already knows to be behind.
        return CompletableFuture.supplyAsync(mutation, plugin.getBukkitAsyncExecutor()).handle((result, error) -> islandSnapshot.reload(islandUuid).thenCompose(v -> error == null ? CompletableFuture.completedFuture(result) : CompletableFuture.failedFuture(error))).thenCompose(future -> future);
    }

    private CompletableFuture<Void> cleanupFailedCreate(UUID islandUuid, String islandName) {
        return worldHandler.deleteWorld(islandName).exceptionally(e -> {
            plugin.severe("Failed to cleanup world after island create failure: " + islandUuid, e);
            return null;
        }).thenRunAsync(() -> {
            try {
                database.deleteIsland(new Actor.Bypass("island create cleanup"), islandUuid);
            } catch (Exception e) {
                plugin.severe("Failed to cleanup database after island create failure: " + islandUuid, e);
            }

            islandRegistry.removeIslandLoadedServer(islandUuid);
            islandSnapshot.unload(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}
