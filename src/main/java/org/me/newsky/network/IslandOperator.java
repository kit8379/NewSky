package org.me.newsky.network;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.CannotExpelIslandPlayerException;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.WorldNotFoundException;
import org.me.newsky.exceptions.WrongIslandHostException;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Island;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.thread.KeyedSequentialExecutor;
import org.me.newsky.util.LocationUtils;
import org.me.newsky.world.WorldHandler;
import org.me.newsky.snapshot.IslandSnapshot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class IslandOperator {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final IslandSnapshot islandSnapshot;
    private final IslandRegistry islandRegistry;
    private final String serverID;

    private final Map<UUID, CompletableFuture<Void>> loadsInFlight = new ConcurrentHashMap<>();

    // Serializes create/load/unload/delete per island on this server. Without it, an unload
    // finishing after a concurrent re-load could release the claim the re-load just took, leaving
    // the world loaded here while another server is free to claim and load it a second time.
    private final KeyedSequentialExecutor<UUID> lifecycleChains = new KeyedSequentialExecutor<>();

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
        return serialized(islandUuid, () -> doCreateIsland(islandUuid, ownerUuid));
    }

    private CompletableFuture<Void> doCreateIsland(UUID islandUuid, UUID ownerUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);
        AtomicBoolean databaseCreated = new AtomicBoolean(false);

        return CompletableFuture.runAsync(() -> {
            database.createIsland(islandUuid, ownerUuid);
            databaseCreated.set(true);

            // The UUID is fresh, so only a stale replayed request can contest this claim - and a
            // replay losing here is exactly the point: creation must not proceed unclaimed.
            if (!islandRegistry.claimHost(islandUuid, serverID)) {
                throw new IslandAlreadyLoadedException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandSnapshot.load(islandUuid);
        }).thenCompose(v -> {
            return worldHandler.createWorld(islandName);
        }).thenRun(() -> {
            plugin.debug("IslandOperator", "Created island " + islandUuid + " on server: " + serverID);
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

        serialized(islandUuid, () -> doLoadIsland(islandUuid)).whenComplete((result, error) -> {
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

        // The claim is re-verified here, at the point of effect: a load request can arrive
        // arbitrarily late (backed-up inbox, replay after restart), and by then the claim may
        // point at another server that is already hosting the world. Loading anyway would put
        // the same world on two servers at once.
        return CompletableFuture.supplyAsync(() -> islandRegistry.claimOrConfirmHost(islandUuid, serverID), plugin.getBukkitAsyncExecutor()).thenCompose(claimHeld -> {
            if (!claimHeld) {
                throw new IslandAlreadyLoadedException();
            }

            return islandSnapshot.load(islandUuid).thenCompose(v -> {
                return worldHandler.loadWorld(islandName);
            }).thenRunAsync(() -> {
                plugin.debug("IslandOperator", "Loaded island " + islandUuid + " on server: " + serverID);
            }, plugin.getBukkitAsyncExecutor()).exceptionallyComposeAsync(e -> {
                // The claim's holder owns its release. Running inside the per-island chain, this
                // cannot race a queued re-load: the chain orders this release before that load's
                // claim, and the compare-and-delete never touches another server's fresh claim.
                islandRegistry.releaseHost(islandUuid, serverID);
                return CompletableFuture.failedFuture(e);
            }, plugin.getBukkitAsyncExecutor());
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return serialized(islandUuid, () -> doUnloadIsland(islandUuid));
    }

    private CompletableFuture<Void> doUnloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return worldHandler.unloadWorld(islandName).thenRunAsync(() -> {
            islandRegistry.releaseHost(islandUuid, serverID);
            islandSnapshot.unload(islandUuid);
            plugin.debug("IslandOperator", "Released island loaded server for UUID: " + islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid, Actor actor) {
        return serialized(islandUuid, () -> doDeleteIsland(islandUuid, actor));
    }

    private CompletableFuture<Void> doDeleteIsland(UUID islandUuid, Actor actor) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        // Write authority first: deleting beside another server's claim would tear the storage out
        // from under a world it is hosting. Then rows before world: the ownership check and the
        // delete share one transaction, so a refused delete changes nothing, and a world that
        // fails to delete afterwards is only unreachable garbage no island row points at.
        return CompletableFuture.supplyAsync(() -> islandRegistry.acquireWriteAuthority(islandUuid, serverID), plugin.getBukkitAsyncExecutor()).thenCompose(authority -> {
            if (authority == IslandRegistry.WriteAuthority.OTHER) {
                throw new WrongIslandHostException();
            }

            return CompletableFuture.runAsync(() -> database.deleteIsland(islandUuid, actor), plugin.getBukkitAsyncExecutor()).thenCompose(v -> worldHandler.deleteWorld(islandName).exceptionally(e -> {
                plugin.severe("Island rows deleted but the world could not be removed, leaving an orphaned world: " + islandName, e);
                return null;
            })).thenRunAsync(() -> {
                releaseClaimQuietly(islandUuid);
                islandSnapshot.unload(islandUuid);
                plugin.debug("IslandOperator", "Deleted island and released loaded server for UUID: " + islandUuid);
            }, plugin.getBukkitAsyncExecutor()).exceptionallyComposeAsync(e -> {
                // A refused delete changes nothing, so a real host keeps hosting; only the
                // temporary write claim has to be handed back.
                if (authority == IslandRegistry.WriteAuthority.CLAIMED) {
                    releaseClaimQuietly(islandUuid);
                }
                return CompletableFuture.failedFuture(e);
            }, plugin.getBukkitAsyncExecutor());
        });
    }

    public CompletableFuture<Void> prepareTeleport(UUID playerUuid, String teleportWorld, String teleportLocation) {
        // The teleport's own future is part of the result: completing before the move lands (or
        // reporting success for a teleport an event handler cancelled) would tell the caller a
        // lie. A pending teleport for a player still connecting completes immediately - the join
        // listener finishes it on arrival.
        return ensureTeleportWorldLoaded(teleportWorld).thenCompose(v -> CompletableFuture.supplyAsync(() -> {
            Location location = LocationUtils.stringToLocation(teleportWorld, teleportLocation);
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                return player.teleportAsync(location);
            }

            teleportHandler.addPendingTeleport(playerUuid, location);
            return CompletableFuture.completedFuture(true);
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenCompose(teleported -> teleported).thenAccept(arrived -> {
            if (!Boolean.TRUE.equals(arrived)) {
                throw new IllegalStateException("Teleport was blocked at the destination");
            }
        })).thenRunAsync(() -> {
            plugin.debug("IslandOperator", "Teleported player " + playerUuid + " to location: " + teleportLocation + " in world: " + teleportWorld);
        }, plugin.getBukkitAsyncExecutor());
    }

    /**
     * Self-heal at the point of effect: the routing that sent this teleport here read the claim
     * registry, but the world can be gone by arrival (unloaded in between, or a claim left
     * dangling by a crash mid-handshake). Re-loading the island here - where the claim either
     * confirms or the load refuses - repairs the dangling case instead of bouncing every
     * teleport to this island forever.
     */
    private CompletableFuture<Void> ensureTeleportWorldLoaded(String worldName) {
        return CompletableFuture.supplyAsync(() -> Bukkit.getWorld(worldName) != null, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenCompose(loaded -> {
            if (loaded) {
                return CompletableFuture.completedFuture(null);
            }

            UUID islandUuid = IslandUtils.parseIslandUuid(worldName);
            if (islandUuid == null) {
                return CompletableFuture.failedFuture(new WorldNotFoundException());
            }

            return loadIsland(islandUuid);
        });
    }

    /**
     * Kicks a visitor out of the island world. The membership guard runs here - on the island's
     * host server, at the moment of the kick - not only at the caller: the caller's check and this
     * kick are separated by a cross-server hop, and the target may have become a member in between.
     * Guarding at the point of effect shrinks that window to the gap between a membership commit
     * and this read. A member slipping through even that is only bounced to the lobby once.
     */
    public CompletableFuture<Void> expelPlayer(UUID islandUuid, Actor actor, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).keySet(), plugin.getBukkitAsyncExecutor()).thenCompose(islandPlayers -> {
            // The actor's authority is re-read here too: the caller's check and this kick are
            // separated by a cross-server hop, and the actor may have lost membership in between.
            if (actor instanceof Actor.Player player && !islandPlayers.contains(player.uuid())) {
                throw new IslandDoesNotExistException();
            }

            if (islandPlayers.contains(playerUuid)) {
                throw new CannotExpelIslandPlayerException();
            }

            return worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid);
        });
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role, UUID vouchedBy) {
        return authorizedWrite(islandUuid, () -> {
            database.addMember(islandUuid, playerUuid, role, vouchedBy);
            return null;
        }, result -> island -> island.withMemberAdded(playerUuid));
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>authorizedWrite(islandUuid, () -> {
            database.removeMember(islandUuid, actor, playerUuid);
            return null;
        }, result -> island -> island.withMemberRemoved(playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, Actor actor, UUID newOwnerUuid) {
        return authorizedWrite(islandUuid, () -> {
            database.setOwner(islandUuid, actor, newOwnerUuid);
            return null;
        }, result -> island -> island.withOwner(newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>authorizedWrite(islandUuid, () -> {
            database.addBan(islandUuid, actor, playerUuid);
            return null;
        }, result -> island -> island.withBanAdded(playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> removeBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        return authorizedWrite(islandUuid, () -> {
            database.removeBan(islandUuid, actor, playerUuid);
            return null;
        }, result -> island -> island.withBanRemoved(playerUuid));
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return authorizedWrite(islandUuid, () -> {
            database.addCoop(islandUuid, actor, playerUuid);
            return null;
        }, result -> island -> island.withCoopAdded(playerUuid));
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>authorizedWrite(islandUuid, () -> {
            database.removeCoop(islandUuid, actor, playerUuid);
            return null;
        }, result -> island -> island.withCoopRemoved(playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Boolean> toggleLock(UUID islandUuid, Actor actor) {
        return this.<Boolean>authorizedWrite(islandUuid, () -> database.toggleLock(islandUuid, actor), locked -> island -> island.withLock(locked)).thenCompose(locked -> {
            if (!locked) {
                return CompletableFuture.completedFuture(false);
            }

            return removeNonMembersFromWorld(islandUuid).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> togglePvp(UUID islandUuid, Actor actor) {
        return authorizedWrite(islandUuid, () -> database.togglePvp(islandUuid, actor), pvp -> island -> island.withPvp(pvp));
    }

    private CompletableFuture<Void> removeNonMembersFromWorld(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).keySet(), plugin.getBukkitAsyncExecutor()).thenCompose(islandPlayers -> worldHandler.removePlayersFromWorld(islandName, player -> !islandPlayers.contains(player.getUniqueId())));
    }

    /**
     * Runs a state write with island write authority, all inside the island's chain slot:
     * <ol>
     *   <li><b>HOST</b> - this server holds the claim: commit, then apply the write's own delta to
     *       the hosted copy. No re-read, no refresh: the write completes only after memory
     *       reflects it, which is what makes enforcement staleness on the host effectively zero.</li>
     *   <li><b>CLAIMED</b> - the island was unclaimed, so this server takes a temporary write
     *       claim for the duration and releases it inside the same slot. A concurrent load routed
     *       here (the claim points at us) queues behind this slot, so its seed always reads the
     *       committed state - the write-versus-load race is unrepresentable, not detected.</li>
     *   <li><b>OTHER</b> - another server holds the claim (the island moved after routing):
     *       executing here would commit a change the real host's memory never hears about, so the
     *       write is refused with {@link WrongIslandHostException} and the caller re-routes.</li>
     * </ol>
     * Running inside the chain also keeps delta order equal to commit order: two writes to one
     * island cannot commit in one order and apply in the other.
     */
    private <T> CompletableFuture<T> authorizedWrite(UUID islandUuid, Supplier<T> committedWrite, Function<T, UnaryOperator<Island>> deltaFor) {
        return serialized(islandUuid, () -> CompletableFuture.supplyAsync(() -> islandRegistry.acquireWriteAuthority(islandUuid, serverID), plugin.getBukkitAsyncExecutor()).thenCompose(authority -> {
            if (authority == IslandRegistry.WriteAuthority.OTHER) {
                throw new WrongIslandHostException();
            }

            CompletableFuture<T> write = CompletableFuture.supplyAsync(committedWrite, plugin.getBukkitAsyncExecutor()).thenCompose(result -> islandSnapshot.apply(islandUuid, deltaFor.apply(result)).thenApply(v -> result));

            if (authority == IslandRegistry.WriteAuthority.HOST) {
                return write;
            }

            return write.whenCompleteAsync((result, error) -> releaseClaimQuietly(islandUuid), plugin.getBukkitAsyncExecutor());
        }));
    }

    private void releaseClaimQuietly(UUID islandUuid) {
        try {
            islandRegistry.releaseHost(islandUuid, serverID);
        } catch (Exception e) {
            // The write itself succeeded; a failed release only leaves a claim pointing at this
            // live server, which self-heals: the next teleport routed here loads the island.
            plugin.severe("Failed to release temporary write claim for island: " + islandUuid, e);
        }
    }

    /**
     * Runs one lifecycle operation at a time per island on this server. Operations for different
     * islands run freely in parallel; a failed operation never blocks the ones queued behind it.
     */
    private <T> CompletableFuture<T> serialized(UUID islandUuid, Supplier<CompletableFuture<T>> operation) {
        return lifecycleChains.submit(islandUuid, operation);
    }

    private CompletableFuture<Void> cleanupFailedCreate(UUID islandUuid, String islandName) {
        return worldHandler.deleteWorld(islandName).exceptionally(e -> {
            plugin.severe("Failed to cleanup world after island create failure: " + islandUuid, e);
            return null;
        }).thenRunAsync(() -> {
            try {
                database.deleteIsland(islandUuid, new Actor.Bypass("island create cleanup"));
            } catch (Exception e) {
                plugin.severe("Failed to cleanup database after island create failure: " + islandUuid, e);
            }

            islandRegistry.releaseHost(islandUuid, serverID);
            islandSnapshot.unload(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}
