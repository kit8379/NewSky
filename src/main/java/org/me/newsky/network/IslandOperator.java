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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class IslandOperator {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final IslandSnapshot islandSnapshot;
    private final IslandRegistry islandRegistry;
    private final IslandRegistry.HostClaim hostClaim;
    private final UUID writeEpoch;

    private final Map<UUID, CompletableFuture<Void>> loadsInFlight = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingOperations = new AtomicBoolean(true);
    private final AtomicInteger activeOperations = new AtomicInteger();
    private final Object admissionLock = new Object();

    // Serializes create/load/unload/delete per island on this server. Without it, an unload
    // finishing after a concurrent re-load could release the claim the re-load just took, leaving
    // the world loaded here while another server is free to claim and load it a second time.
    private final KeyedSequentialExecutor<UUID> lifecycleChains = new KeyedSequentialExecutor<>();

    private record VersionedResult<T>(T value, long version) {
    }

    private static final class FencedIslandClaimException extends IllegalStateException {
        private FencedIslandClaimException(UUID islandUuid) {
            super("Island claim was fenced during lifecycle operation: " + islandUuid);
        }
    }

    public IslandOperator(NewSky plugin, DatabaseHandler database, WorldHandler worldHandler, TeleportHandler teleportHandler, IslandSnapshot islandSnapshot, IslandRegistry islandRegistry, IslandRegistry.HostClaim hostClaim) {
        this.plugin = plugin;
        this.database = database;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.islandSnapshot = islandSnapshot;
        this.islandRegistry = islandRegistry;
        this.hostClaim = hostClaim;
        this.writeEpoch = UUID.fromString(hostClaim.instanceId());
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        return serialized(islandUuid, () -> doCreateIsland(islandUuid, ownerUuid));
    }

    private CompletableFuture<Void> doCreateIsland(UUID islandUuid, UUID ownerUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);
        AtomicBoolean claimTaken = new AtomicBoolean(false);
        AtomicBoolean databaseCreated = new AtomicBoolean(false);

        return CompletableFuture.runAsync(() -> {
            // Claim before publishing the database row. Once createIsland commits, owner lookups
            // expose the UUID to every server; claiming afterwards leaves a window in which a
            // teleport can claim/load it elsewhere and our failure cleanup deletes that live data.
            if (!islandRegistry.claimHost(islandUuid, hostClaim)) {
                throw new IslandAlreadyLoadedException();
            }
            claimTaken.set(true);

            database.createIsland(islandUuid, ownerUuid, writeEpoch);
            databaseCreated.set(true);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> requireLiveClaim(islandUuid)).thenCompose(v -> {
            return islandSnapshot.load(islandUuid);
        }).thenCompose(v -> {
            return worldHandler.createWorld(islandName);
        }).thenCompose(v -> requireLiveClaim(islandUuid))
                .thenRunAsync(() -> database.markIslandReady(islandUuid, writeEpoch), plugin.getBukkitAsyncExecutor())
                .thenCompose(v -> requireLiveClaim(islandUuid)).thenRun(() -> {
            plugin.debug("IslandOperator", "Created island " + islandUuid + " on server instance: " + hostClaim.encoded());
        }).exceptionallyCompose(e -> {
            if (!claimTaken.get()) {
                return CompletableFuture.failedFuture(e);
            }

            return cleanupFailedCreate(islandUuid, islandName, databaseCreated.get()).thenCompose(v -> CompletableFuture.failedFuture(e));
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
        return CompletableFuture.supplyAsync(() -> Bukkit.getWorld(islandName) != null,
                Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenCompose(alreadyLoaded ->
                CompletableFuture.supplyAsync(() -> islandRegistry.claimOrConfirmHost(islandUuid, hostClaim), plugin.getBukkitAsyncExecutor()).thenCompose(claimHeld -> {
            if (!claimHeld) {
                throw new IslandAlreadyLoadedException();
            }

            CompletableFuture<Void> prepared = CompletableFuture.runAsync(
                            () -> database.bindWriteEpoch(islandUuid, writeEpoch), plugin.getBukkitAsyncExecutor())
                    // Redis and MySQL cannot share one transaction. Re-checking the live claim
                    // after binding the durable epoch closes the hand-off window before any world
                    // or snapshot side effect begins; the transaction itself checks the epoch too.
                    .thenCompose(v -> requireLiveClaim(islandUuid));
            CompletableFuture<Boolean> provisioning = prepared.thenCompose(v -> CompletableFuture.supplyAsync(
                    () -> database.isIslandProvisioning(islandUuid), plugin.getBukkitAsyncExecutor()));

            if (alreadyLoaded) {
                return provisioning.thenCompose(resumeCreate -> islandSnapshot.get(islandUuid) == null
                                ? islandSnapshot.load(islandUuid)
                                : CompletableFuture.completedFuture(null))
                        .thenCompose(v -> requireLiveClaim(islandUuid))
                        .thenCompose(v -> provisioning.thenCompose(resumeCreate ->
                                markReadyIfProvisioning(islandUuid, resumeCreate)))
                        .thenRun(() ->
                                plugin.debug("IslandOperator", "Island already loaded and claim re-confirmed: " + islandUuid))
                        .exceptionallyCompose(e -> {
                            if (unwrap(e) instanceof FencedIslandClaimException) {
                                return unloadAfterFailedLoad(islandUuid, islandName)
                                        .thenCompose(v -> CompletableFuture.failedFuture(e));
                            }
                            return CompletableFuture.failedFuture(e);
                        });
            }

            return provisioning.thenCompose(resumeCreate -> islandSnapshot.load(islandUuid)
                    .thenCompose(v -> resumeCreate
                            ? worldHandler.resumeProvisioningWorld(islandName)
                            : worldHandler.loadWorld(islandName))
                    .thenCompose(v -> requireLiveClaim(islandUuid))
                    .thenCompose(v -> markReadyIfProvisioning(islandUuid, resumeCreate))).thenRunAsync(() -> {
                plugin.debug("IslandOperator", "Loaded island " + islandUuid + " on server instance: " + hostClaim.encoded());
            }, plugin.getBukkitAsyncExecutor()).exceptionallyComposeAsync(e -> {
                // The claim's holder owns its release. Running inside the per-island chain, this
                // cannot race a queued re-load: the chain orders this release before that load's
                // claim, and the compare-and-delete never touches another server's fresh claim.
                return unloadAfterFailedLoad(islandUuid, islandName).thenCompose(v -> CompletableFuture.failedFuture(e));
            }, plugin.getBukkitAsyncExecutor());
        }));
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return serialized(islandUuid, () -> doUnloadIsland(islandUuid));
    }

    /** Idle unload variant whose final eligibility check executes inside the lifecycle slot. */
    public CompletableFuture<Boolean> unloadIslandIfIdle(UUID islandUuid, BooleanSupplier stillIdle) {
        return serialized(islandUuid, () -> doUnloadIslandIfIdle(islandUuid, stillIdle));
    }

    private CompletableFuture<Void> doUnloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return CompletableFuture.supplyAsync(() -> islandRegistry.holdsLiveClaim(islandUuid, hostClaim),
                plugin.getBukkitAsyncExecutor()).thenCompose(live -> live
                ? worldHandler.unloadWorld(islandName)
                : worldHandler.unloadWorldFromBukkit(islandName)).thenRunAsync(() -> {
            islandRegistry.releaseHost(islandUuid, hostClaim);
            islandSnapshot.unload(islandUuid);
            plugin.debug("IslandOperator", "Released island loaded server for UUID: " + islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Boolean> doUnloadIslandIfIdle(UUID islandUuid, BooleanSupplier stillIdle) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return CompletableFuture.supplyAsync(() -> islandRegistry.holdsLiveClaim(islandUuid, hostClaim),
                plugin.getBukkitAsyncExecutor()).thenCompose(live -> live
                ? worldHandler.unloadWorldIfIdle(islandName, stillIdle)
                : worldHandler.unloadWorldFromBukkit(islandName).thenApply(v -> true)).thenApplyAsync(unloaded -> {
            if (unloaded) {
                islandRegistry.releaseHost(islandUuid, hostClaim);
                islandSnapshot.unload(islandUuid);
                plugin.debug("IslandOperator", "Released idle island host for UUID: " + islandUuid);
            }
            return unloaded;
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
        return CompletableFuture.supplyAsync(() -> islandRegistry.acquireWriteAuthority(islandUuid, hostClaim), plugin.getBukkitAsyncExecutor()).thenCompose(authority -> {
            if (authority == IslandRegistry.WriteAuthority.OTHER) {
                throw new WrongIslandHostException();
            }
            if (authority == IslandRegistry.WriteAuthority.FENCED) {
                throw new IllegalStateException("This server instance has lost its cluster lease");
            }

            return CompletableFuture.runAsync(() -> database.bindWriteEpoch(islandUuid, writeEpoch), plugin.getBukkitAsyncExecutor())
                    .thenCompose(v -> requireLiveClaim(islandUuid))
                    .thenRunAsync(() -> database.deleteIsland(islandUuid, actor, writeEpoch), plugin.getBukkitAsyncExecutor()).thenCompose(v -> worldHandler.deleteWorld(islandName).exceptionally(e -> {
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
        return authorizedWrite(islandUuid,
                () -> new VersionedResult<>(null, database.addMember(islandUuid, playerUuid, role, vouchedBy, writeEpoch)),
                result -> island -> island.withMemberAdded(playerUuid));
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>authorizedWrite(islandUuid,
                () -> new VersionedResult<>(null, database.removeMember(islandUuid, actor, playerUuid, writeEpoch)),
                result -> island -> island.withMemberRemoved(playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, Actor actor, UUID newOwnerUuid) {
        return authorizedWrite(islandUuid,
                () -> new VersionedResult<>(null, database.setOwner(islandUuid, actor, newOwnerUuid, writeEpoch)),
                result -> island -> island.withOwner(newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>authorizedWrite(islandUuid,
                () -> new VersionedResult<>(null, database.addBan(islandUuid, actor, playerUuid, writeEpoch)),
                result -> island -> island.withBanAdded(playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Void> removeBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        return authorizedWrite(islandUuid,
                () -> new VersionedResult<>(null, database.removeBan(islandUuid, actor, playerUuid, writeEpoch)),
                result -> island -> island.withBanRemoved(playerUuid));
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return authorizedWrite(islandUuid,
                () -> new VersionedResult<>(null, database.addCoop(islandUuid, actor, playerUuid, writeEpoch)),
                result -> island -> island.withCoopAdded(playerUuid));
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>authorizedWrite(islandUuid,
                () -> new VersionedResult<>(null, database.removeCoop(islandUuid, actor, playerUuid, writeEpoch)),
                result -> island -> island.withCoopRemoved(playerUuid)).thenCompose(v -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    public CompletableFuture<Boolean> toggleLock(UUID islandUuid, Actor actor) {
        return toggleLock(islandUuid, actor, UUID.randomUUID());
    }

    public CompletableFuture<Boolean> toggleLock(UUID islandUuid, Actor actor, UUID operationId) {
        return this.<Boolean>authorizedWrite(islandUuid, () -> {
            DatabaseHandler.VersionedBoolean mutation = database.toggleLockVersioned(islandUuid, actor, operationId, writeEpoch);
            return new VersionedResult<>(mutation.value(), mutation.version());
        }, locked -> island -> island.withLock(locked)).thenCompose(locked -> {
            if (!locked) {
                return CompletableFuture.completedFuture(false);
            }

            return removeNonMembersFromWorld(islandUuid).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> togglePvp(UUID islandUuid, Actor actor) {
        return togglePvp(islandUuid, actor, UUID.randomUUID());
    }

    public CompletableFuture<Boolean> togglePvp(UUID islandUuid, Actor actor, UUID operationId) {
        return authorizedWrite(islandUuid, () -> {
            DatabaseHandler.VersionedBoolean mutation = database.togglePvpVersioned(islandUuid, actor, operationId, writeEpoch);
            return new VersionedResult<>(mutation.value(), mutation.version());
        }, pvp -> island -> island.withPvp(pvp));
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
    private <T> CompletableFuture<T> authorizedWrite(UUID islandUuid, Supplier<VersionedResult<T>> committedWrite, Function<T, UnaryOperator<Island>> deltaFor) {
        return serialized(islandUuid, () -> CompletableFuture.supplyAsync(() -> islandRegistry.acquireWriteAuthority(islandUuid, hostClaim), plugin.getBukkitAsyncExecutor()).thenCompose(authority -> {
            if (authority == IslandRegistry.WriteAuthority.OTHER) {
                throw new WrongIslandHostException();
            }
            if (authority == IslandRegistry.WriteAuthority.FENCED) {
                throw new IllegalStateException("This server instance has lost its cluster lease");
            }

            CompletableFuture<T> write = CompletableFuture.runAsync(() -> database.bindWriteEpoch(islandUuid, writeEpoch),
                    plugin.getBukkitAsyncExecutor()).thenCompose(v -> requireLiveClaim(islandUuid))
                    .thenCompose(v -> CompletableFuture.supplyAsync(committedWrite, plugin.getBukkitAsyncExecutor())).thenCompose(result ->
                    islandSnapshot.applyVersioned(islandUuid, result.version(), deltaFor.apply(result.value())).thenApply(v -> result.value()));

            if (authority == IslandRegistry.WriteAuthority.HOST) {
                return write;
            }

            return write.whenCompleteAsync((result, error) -> releaseClaimQuietly(islandUuid), plugin.getBukkitAsyncExecutor());
        }));
    }

    private void releaseClaimQuietly(UUID islandUuid) {
        try {
            islandRegistry.releaseHost(islandUuid, hostClaim);
        } catch (Exception e) {
            // The write itself succeeded; a failed release only leaves a claim pointing at this
            // live server, which self-heals: the next teleport routed here loads the island.
            plugin.severe("Failed to release temporary write claim for island: " + islandUuid, e);
        }
    }

    private CompletableFuture<Void> requireLiveClaim(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> islandRegistry.holdsLiveClaim(islandUuid, hostClaim),
                plugin.getBukkitAsyncExecutor()).thenAccept(live -> {
            if (!live) {
                throw new FencedIslandClaimException(islandUuid);
            }
        });
    }

    private CompletableFuture<Void> markReadyIfProvisioning(UUID islandUuid, boolean provisioning) {
        if (!provisioning) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> database.markIslandReady(islandUuid, writeEpoch),
                        plugin.getBukkitAsyncExecutor())
                .thenCompose(v -> requireLiveClaim(islandUuid));
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private CompletableFuture<Void> unloadAfterFailedLoad(UUID islandUuid, String islandName) {
        // A fenced instance must not save into shared storage: a newer host may already own and
        // persist this same world. Only remove the local Bukkit instance and exact old claim.
        return worldHandler.unloadWorldFromBukkit(islandName).exceptionally(error -> {
            plugin.severe("Failed to locally unload world after failed/fenced load: " + islandUuid, error);
            return null;
        }).thenRunAsync(() -> {
            islandSnapshot.unload(islandUuid);
            islandRegistry.releaseHost(islandUuid, hostClaim);
        }, plugin.getBukkitAsyncExecutor());
    }

    /**
     * Runs one lifecycle operation at a time per island on this server. Operations for different
     * islands run freely in parallel; a failed operation never blocks the ones queued behind it.
     */
    private <T> CompletableFuture<T> serialized(UUID islandUuid, Supplier<CompletableFuture<T>> operation) {
        synchronized (admissionLock) {
            if (!acceptingOperations.get()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Island operator is shutting down"));
            }
            activeOperations.incrementAndGet();
        }
        CompletableFuture<T> future = lifecycleChains.submit(islandUuid, operation);
        future.whenComplete((result, error) -> activeOperations.decrementAndGet());
        return future;
    }

    /**
     * Closes admission atomically and reports whether already-admitted work still exists. When it
     * does, shutdown leaves the heartbeat/claims to expire instead of releasing them underneath
     * a database commit that may still be finishing.
     */
    public boolean stopAcceptingOperations() {
        synchronized (admissionLock) {
            acceptingOperations.set(false);
            return activeOperations.get() > 0;
        }
    }

    private CompletableFuture<Void> cleanupFailedCreate(UUID islandUuid, String islandName, boolean databaseCreated) {
        // Cleanup authority is the durable MySQL epoch, not a Redis observation. The create may
        // have committed just before its lease expired. In that case requiring the old Redis claim
        // would strand an owner-visible row with no world forever. Conversely, if a new host has
        // already rebound the row, deleteIsland's FOR UPDATE epoch check refuses this cleanup.
        CompletableFuture<Boolean> rowsDeleted;
        if (databaseCreated) {
            rowsDeleted = CompletableFuture.supplyAsync(() -> {
                try {
                    database.deleteIsland(islandUuid, new Actor.Bypass("island create cleanup"), writeEpoch);
                    return true;
                } catch (Exception e) {
                    plugin.severe("Failed or fenced database cleanup after island create failure: " + islandUuid, e);
                    return false;
                }
            }, plugin.getBukkitAsyncExecutor());
        } else {
            rowsDeleted = CompletableFuture.completedFuture(false);
        }

        return rowsDeleted.thenCompose(deleted -> deleted
                ? worldHandler.deleteWorld(islandName).exceptionally(e -> {
                    plugin.severe("Island rows were cleaned but failed to delete create's orphaned world: " + islandUuid, e);
                    return null;
                })
                : worldHandler.unloadWorldFromBukkit(islandName).exceptionally(e -> {
                    plugin.severe("Failed to locally unload world after fenced island create: " + islandUuid, e);
                    return null;
                })).thenRunAsync(() -> {

            islandRegistry.releaseHost(islandUuid, hostClaim);
            islandSnapshot.unload(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}
