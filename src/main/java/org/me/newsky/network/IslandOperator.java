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
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.thread.KeyedSequentialExecutor;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;
import org.me.newsky.world.WorldHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
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
    private final IslandRegistry.HostClaim hostClaim;
    private final UUID writeEpoch;

    private final Map<UUID, CompletableFuture<Void>> loadsInFlight = new ConcurrentHashMap<>();
    private final KeyedSequentialExecutor<UUID> lifecycleChains = new KeyedSequentialExecutor<>();
    private final Object admissionLock = new Object();

    private boolean acceptingOperations = true;
    private int activeOperations;

    private record VersionedResult<T>(T value, long version) {
    }

    private static final class FencedIslandClaimException extends IllegalStateException {
        private FencedIslandClaimException(UUID islandUuid) {
            super("Island claim was fenced during lifecycle operation: " + islandUuid);
        }
    }

    public IslandOperator(NewSky plugin, DatabaseHandler database, WorldHandler worldHandler,
                          TeleportHandler teleportHandler, IslandSnapshot islandSnapshot,
                          IslandRegistry islandRegistry, IslandRegistry.HostClaim hostClaim) {
        this.plugin = plugin;
        this.database = database;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.islandSnapshot = islandSnapshot;
        this.islandRegistry = islandRegistry;
        this.hostClaim = hostClaim;
        this.writeEpoch = UUID.fromString(hostClaim.instanceId());
    }

    // ================================================================================================================
    // Island lifecycle
    // ================================================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        return serialized(islandUuid, () -> createIslandNow(islandUuid, ownerUuid));
    }

    private CompletableFuture<Void> createIslandNow(UUID islandUuid, UUID ownerUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);
        CreateProgress progress = new CreateProgress();

        return claimAndCreateDatabaseRows(islandUuid, ownerUuid, progress)
                .thenCompose(v -> islandSnapshot.load(islandUuid))
                .thenCompose(v -> worldHandler.createWorld(islandName))
                .thenCompose(v -> requireLiveClaim(islandUuid))
                .thenRunAsync(() -> database.markIslandReady(islandUuid, writeEpoch), plugin.getBukkitAsyncExecutor())
                .thenCompose(v -> requireLiveClaim(islandUuid))
                .thenRun(() -> plugin.debug("IslandOperator", "Created island " + islandUuid
                        + " on server instance: " + hostClaim.encoded()))
                .exceptionallyCompose(error -> {
                    if (!progress.claimTaken) {
                        return CompletableFuture.failedFuture(error);
                    }

                    return cleanupFailedCreate(islandUuid, islandName, progress.databaseCreated)
                            .thenCompose(v -> CompletableFuture.failedFuture(error));
                });
    }

    private CompletableFuture<Void> claimAndCreateDatabaseRows(UUID islandUuid, UUID ownerUuid,
                                                                CreateProgress progress) {
        return CompletableFuture.runAsync(() -> {
            // Claim first. Once the owner row is visible, another server may try to load it.
            if (!islandRegistry.claimHost(islandUuid, hostClaim)) {
                throw new IslandAlreadyLoadedException();
            }

            progress.claimTaken = true;
            database.createIsland(islandUuid, ownerUuid, writeEpoch);
            progress.databaseCreated = true;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        CompletableFuture<Void> currentLoad = loadsInFlight.get(islandUuid);
        if (currentLoad != null) {
            return currentLoad;
        }

        CompletableFuture<Void> loadGate = new CompletableFuture<>();
        currentLoad = loadsInFlight.putIfAbsent(islandUuid, loadGate);
        if (currentLoad != null) {
            return currentLoad;
        }

        serialized(islandUuid, () -> loadIslandNow(islandUuid)).whenComplete((result, error) -> {
            loadsInFlight.remove(islandUuid, loadGate);

            if (error == null) {
                loadGate.complete(null);
            } else {
                loadGate.completeExceptionally(error);
            }
        });

        return loadGate;
    }

    private CompletableFuture<Void> loadIslandNow(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return isWorldLoaded(islandName).thenCompose(alreadyLoaded -> {
            return claimAndPrepareLoad(islandUuid).thenCompose(provisioning -> {
                return loadIslandContents(islandUuid, islandName, alreadyLoaded, provisioning);
            }).exceptionallyCompose(error -> {
                if (alreadyLoaded && !(unwrap(error) instanceof FencedIslandClaimException)) {
                    return CompletableFuture.failedFuture(error);
                }

                return unloadAfterFailedLoad(islandUuid, islandName)
                        .thenCompose(v -> CompletableFuture.failedFuture(error));
            });
        }).thenRunAsync(() -> {
            plugin.debug("IslandOperator", "Loaded island " + islandUuid
                    + " on server instance: " + hostClaim.encoded());
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Boolean> isWorldLoaded(String islandName) {
        return CompletableFuture.supplyAsync(() -> Bukkit.getWorld(islandName) != null,
                Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    private CompletableFuture<Boolean> claimAndPrepareLoad(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!islandRegistry.claimOrConfirmHost(islandUuid, hostClaim)) {
                throw new IslandAlreadyLoadedException();
            }

            database.bindWriteEpoch(islandUuid, writeEpoch);
            return database.isIslandProvisioning(islandUuid);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(provisioning -> {
            return requireLiveClaim(islandUuid).thenApply(v -> provisioning);
        });
    }

    private CompletableFuture<Void> loadIslandContents(UUID islandUuid, String islandName,
                                                       boolean alreadyLoaded, boolean provisioning) {
        CompletableFuture<Void> loadSnapshot;
        if (alreadyLoaded && islandSnapshot.get(islandUuid) != null) {
            loadSnapshot = CompletableFuture.completedFuture(null);
        } else {
            loadSnapshot = islandSnapshot.load(islandUuid);
        }

        CompletableFuture<Void> loadWorld = loadSnapshot.thenCompose(v -> {
            if (alreadyLoaded) {
                return CompletableFuture.completedFuture(null);
            }
            if (provisioning) {
                return worldHandler.resumeProvisioningWorld(islandName);
            }
            return worldHandler.loadWorld(islandName);
        });

        return loadWorld
                .thenCompose(v -> requireLiveClaim(islandUuid))
                .thenCompose(v -> markReadyIfProvisioning(islandUuid, provisioning));
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return serialized(islandUuid, () -> unloadIslandNow(islandUuid));
    }

    public CompletableFuture<Boolean> unloadIslandIfIdle(UUID islandUuid, BooleanSupplier stillIdle) {
        return serialized(islandUuid, () -> unloadIslandIfIdleNow(islandUuid, stillIdle));
    }

    private CompletableFuture<Void> unloadIslandNow(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return hasLiveClaim(islandUuid).thenCompose(liveClaim -> {
            if (liveClaim) {
                return worldHandler.unloadWorld(islandName);
            }
            return worldHandler.unloadWorldFromBukkit(islandName);
        }).thenRunAsync(() -> finishUnload(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Boolean> unloadIslandIfIdleNow(UUID islandUuid, BooleanSupplier stillIdle) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return hasLiveClaim(islandUuid).thenCompose(liveClaim -> {
            if (liveClaim) {
                return worldHandler.unloadWorldIfIdle(islandName, stillIdle);
            }
            return worldHandler.unloadWorldFromBukkit(islandName).thenApply(v -> true);
        }).thenApplyAsync(unloaded -> {
            if (unloaded) {
                finishUnload(islandUuid);
            }
            return unloaded;
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Boolean> hasLiveClaim(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> islandRegistry.holdsLiveClaim(islandUuid, hostClaim),
                plugin.getBukkitAsyncExecutor());
    }

    private void finishUnload(UUID islandUuid) {
        islandRegistry.releaseHost(islandUuid, hostClaim);
        islandSnapshot.unload(islandUuid);
        plugin.debug("IslandOperator", "Released island host for UUID: " + islandUuid);
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid, Actor actor) {
        return serialized(islandUuid, () -> deleteIslandNow(islandUuid, actor));
    }

    private CompletableFuture<Void> deleteIslandNow(UUID islandUuid, Actor actor) {
        return getWriteAuthority(islandUuid).thenCompose(authority -> {
            requireLocalAuthority(authority);
            return deleteIslandWithAuthority(islandUuid, actor, authority);
        });
    }

    private CompletableFuture<Void> deleteIslandWithAuthority(UUID islandUuid, Actor actor,
                                                               IslandRegistry.WriteAuthority authority) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        CompletableFuture<Void> delete = bindWriteEpoch(islandUuid)
                .thenCompose(v -> requireLiveClaim(islandUuid))
                .thenRunAsync(() -> database.deleteIsland(islandUuid, actor, writeEpoch),
                        plugin.getBukkitAsyncExecutor())
                .thenCompose(v -> deleteWorldAfterDatabase(islandName))
                .thenRunAsync(() -> {
                    releaseClaimQuietly(islandUuid);
                    islandSnapshot.unload(islandUuid);
                    plugin.debug("IslandOperator", "Deleted island and released host for UUID: " + islandUuid);
                }, plugin.getBukkitAsyncExecutor());

        return delete.exceptionallyComposeAsync(error -> {
            if (authority == IslandRegistry.WriteAuthority.CLAIMED) {
                releaseClaimQuietly(islandUuid);
            }
            return CompletableFuture.failedFuture(error);
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Void> deleteWorldAfterDatabase(String islandName) {
        return worldHandler.deleteWorld(islandName).exceptionally(error -> {
            plugin.severe("Island rows were deleted but world cleanup failed: " + islandName, error);
            return null;
        });
    }

    // ================================================================================================================
    // Teleport and world actions
    // ================================================================================================================

    public CompletableFuture<Void> prepareTeleport(UUID playerUuid, String teleportWorld, String teleportLocation) {
        return ensureTeleportWorldLoaded(teleportWorld)
                .thenCompose(v -> teleportPlayer(playerUuid, teleportWorld, teleportLocation))
                .thenRunAsync(() -> {
                    plugin.debug("IslandOperator", "Teleported player " + playerUuid
                            + " to location: " + teleportLocation + " in world: " + teleportWorld);
                }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Void> teleportPlayer(UUID playerUuid, String worldName, String locationText) {
        return CompletableFuture.supplyAsync(() -> {
            Location location = LocationUtils.stringToLocation(worldName, locationText);
            Player player = Bukkit.getPlayer(playerUuid);

            if (player != null) {
                return player.teleportAsync(location);
            }

            teleportHandler.addPendingTeleport(playerUuid, location);
            return CompletableFuture.completedFuture(true);
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenCompose(result -> result).thenAccept(arrived -> {
            if (!Boolean.TRUE.equals(arrived)) {
                throw new IllegalStateException("Teleport was blocked at the destination");
            }
        });
    }

    private CompletableFuture<Void> ensureTeleportWorldLoaded(String worldName) {
        return isWorldLoaded(worldName).thenCompose(loaded -> {
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

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, Actor actor, UUID playerUuid) {
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

    // ================================================================================================================
    // Island data writes
    // ================================================================================================================

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role, UUID vouchedBy) {
        return writeIsland(islandUuid, () -> {
            long version = database.addMember(islandUuid, playerUuid, role, vouchedBy, writeEpoch);
            return new VersionedResult<>(null, version);
        }, ignored -> island -> island.withMemberAdded(playerUuid));
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>writeIsland(islandUuid, () -> {
            long version = database.removeMember(islandUuid, actor, playerUuid, writeEpoch);
            return new VersionedResult<>(null, version);
        }, ignored -> island -> island.withMemberRemoved(playerUuid)).thenCompose(v -> {
            return worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid);
        });
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, Actor actor, UUID newOwnerUuid) {
        return writeIsland(islandUuid, () -> {
            long version = database.setOwner(islandUuid, actor, newOwnerUuid, writeEpoch);
            return new VersionedResult<>(null, version);
        }, ignored -> island -> island.withOwner(newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>writeIsland(islandUuid, () -> {
            long version = database.addBan(islandUuid, actor, playerUuid, writeEpoch);
            return new VersionedResult<>(null, version);
        }, ignored -> island -> island.withBanAdded(playerUuid)).thenCompose(v -> {
            return worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid);
        });
    }

    public CompletableFuture<Void> removeBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        return writeIsland(islandUuid, () -> {
            long version = database.removeBan(islandUuid, actor, playerUuid, writeEpoch);
            return new VersionedResult<>(null, version);
        }, ignored -> island -> island.withBanRemoved(playerUuid));
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return writeIsland(islandUuid, () -> {
            long version = database.addCoop(islandUuid, actor, playerUuid, writeEpoch);
            return new VersionedResult<>(null, version);
        }, ignored -> island -> island.withCoopAdded(playerUuid));
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return this.<Void>writeIsland(islandUuid, () -> {
            long version = database.removeCoop(islandUuid, actor, playerUuid, writeEpoch);
            return new VersionedResult<>(null, version);
        }, ignored -> island -> island.withCoopRemoved(playerUuid)).thenCompose(v -> {
            return worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid);
        });
    }

    public CompletableFuture<Boolean> toggleLock(UUID islandUuid, Actor actor) {
        return toggleLock(islandUuid, actor, UUID.randomUUID());
    }

    public CompletableFuture<Boolean> toggleLock(UUID islandUuid, Actor actor, UUID operationId) {
        return writeIsland(islandUuid, () -> {
            DatabaseHandler.VersionedBoolean result = database.toggleLockVersioned(
                    islandUuid, actor, operationId, writeEpoch);
            return new VersionedResult<>(result.value(), result.version());
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
        return writeIsland(islandUuid, () -> {
            DatabaseHandler.VersionedBoolean result = database.togglePvpVersioned(
                    islandUuid, actor, operationId, writeEpoch);
            return new VersionedResult<>(result.value(), result.version());
        }, pvp -> island -> island.withPvp(pvp));
    }

    private CompletableFuture<Void> removeNonMembersFromWorld(UUID islandUuid) {
        String islandName = IslandUtils.UUIDToName(islandUuid);

        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).keySet(),
                plugin.getBukkitAsyncExecutor()).thenCompose(islandPlayers -> {
            return worldHandler.removePlayersFromWorld(islandName,
                    player -> !islandPlayers.contains(player.getUniqueId()));
        });
    }

    private <T> CompletableFuture<T> writeIsland(UUID islandUuid,
                                                  Supplier<VersionedResult<T>> databaseWrite,
                                                  Function<T, UnaryOperator<Island>> snapshotUpdate) {
        return serialized(islandUuid, () -> {
            return getWriteAuthority(islandUuid).thenCompose(authority -> {
                requireLocalAuthority(authority);

                CompletableFuture<T> write = executeIslandWrite(islandUuid, databaseWrite, snapshotUpdate);
                if (authority == IslandRegistry.WriteAuthority.HOST) {
                    return write;
                }

                return write.whenCompleteAsync((result, error) -> releaseClaimQuietly(islandUuid),
                        plugin.getBukkitAsyncExecutor());
            });
        });
    }

    private <T> CompletableFuture<T> executeIslandWrite(UUID islandUuid,
                                                         Supplier<VersionedResult<T>> databaseWrite,
                                                         Function<T, UnaryOperator<Island>> snapshotUpdate) {
        return bindWriteEpoch(islandUuid)
                .thenCompose(v -> requireLiveClaim(islandUuid))
                .thenCompose(v -> CompletableFuture.supplyAsync(databaseWrite, plugin.getBukkitAsyncExecutor()))
                .thenCompose(result -> {
                    UnaryOperator<Island> update = snapshotUpdate.apply(result.value());
                    return islandSnapshot.applyVersioned(islandUuid, result.version(), update)
                            .thenApply(v -> result.value());
                });
    }

    private CompletableFuture<IslandRegistry.WriteAuthority> getWriteAuthority(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return islandRegistry.acquireWriteAuthority(islandUuid, hostClaim);
        }, plugin.getBukkitAsyncExecutor());
    }

    private void requireLocalAuthority(IslandRegistry.WriteAuthority authority) {
        if (authority == IslandRegistry.WriteAuthority.OTHER) {
            throw new WrongIslandHostException();
        }
        if (authority == IslandRegistry.WriteAuthority.FENCED) {
            throw new IllegalStateException("This server instance has lost its cluster lease");
        }
    }

    private CompletableFuture<Void> bindWriteEpoch(UUID islandUuid) {
        return CompletableFuture.runAsync(() -> database.bindWriteEpoch(islandUuid, writeEpoch),
                plugin.getBukkitAsyncExecutor());
    }

    // ================================================================================================================
    // Fencing, cleanup and serialization
    // ================================================================================================================

    private CompletableFuture<Void> requireLiveClaim(UUID islandUuid) {
        return hasLiveClaim(islandUuid).thenAccept(live -> {
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
                plugin.getBukkitAsyncExecutor()).thenCompose(v -> requireLiveClaim(islandUuid));
    }

    private void releaseClaimQuietly(UUID islandUuid) {
        try {
            islandRegistry.releaseHost(islandUuid, hostClaim);
        } catch (Exception error) {
            plugin.severe("Failed to release temporary write claim for island: " + islandUuid, error);
        }
    }

    private CompletableFuture<Void> unloadAfterFailedLoad(UUID islandUuid, String islandName) {
        // Never save a world after this process has lost its claim.
        return worldHandler.unloadWorldFromBukkit(islandName).exceptionally(error -> {
            plugin.severe("Failed to locally unload world after failed load: " + islandUuid, error);
            return null;
        }).thenRunAsync(() -> {
            islandSnapshot.unload(islandUuid);
            islandRegistry.releaseHost(islandUuid, hostClaim);
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Void> cleanupFailedCreate(UUID islandUuid, String islandName,
                                                        boolean databaseCreated) {
        CompletableFuture<Boolean> deleteRows;
        if (databaseCreated) {
            deleteRows = deleteCreateRows(islandUuid);
        } else {
            deleteRows = CompletableFuture.completedFuture(false);
        }

        return deleteRows.thenCompose(deleted -> {
            if (deleted) {
                return worldHandler.deleteWorld(islandName).exceptionally(error -> {
                    plugin.severe("Failed to delete orphaned world after create failure: " + islandUuid, error);
                    return null;
                });
            }

            return worldHandler.unloadWorldFromBukkit(islandName).exceptionally(error -> {
                plugin.severe("Failed to locally unload world after fenced create: " + islandUuid, error);
                return null;
            });
        }).thenRunAsync(() -> {
            islandRegistry.releaseHost(islandUuid, hostClaim);
            islandSnapshot.unload(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Boolean> deleteCreateRows(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                database.deleteIsland(islandUuid, new Actor.Bypass("island create cleanup"), writeEpoch);
                return true;
            } catch (Exception error) {
                plugin.severe("Failed or fenced database cleanup after island create failure: "
                        + islandUuid, error);
                return false;
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    private <T> CompletableFuture<T> serialized(UUID islandUuid,
                                                Supplier<CompletableFuture<T>> operation) {
        synchronized (admissionLock) {
            if (!acceptingOperations) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Island operator is shutting down"));
            }
            activeOperations++;
        }

        CompletableFuture<T> future = lifecycleChains.submit(islandUuid, operation);
        future.whenComplete((result, error) -> operationFinished());
        return future;
    }

    private void operationFinished() {
        synchronized (admissionLock) {
            activeOperations--;
        }
    }

    public boolean stopAcceptingOperations() {
        synchronized (admissionLock) {
            acceptingOperations = false;
            return activeOperations > 0;
        }
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class CreateProgress {
        private volatile boolean claimTaken;
        private volatile boolean databaseCreated;
    }
}
