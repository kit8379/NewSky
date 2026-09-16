package org.me.newsky.network;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Island;
import org.me.newsky.snapshot.IslandSnapshot;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.LocationUtils;
import org.me.newsky.world.WorldHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Executes island work on this server. Every operation on a hosted island runs in that
 * island's serial chain, so a load, unload or delete can never interleave with a mutation
 * or a teleport prepare, and an operation that arrives after the island left this server
 * fails typed instead of touching the database without the claim.
 */
public class IslandOperator {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final WorldHandler worldHandler;
    private final TeleportHandler teleportHandler;
    private final IslandSnapshot islandSnapshot;
    private final IslandClaims islandClaims;
    private final String serverID;

    private final Map<UUID, CompletableFuture<Void>> chains = new ConcurrentHashMap<>();
    private final Set<UUID> hosted = ConcurrentHashMap.newKeySet();

    public IslandOperator(NewSky plugin, DatabaseHandler database, WorldHandler worldHandler, TeleportHandler teleportHandler, IslandSnapshot islandSnapshot, IslandClaims islandClaims, String serverID) {
        this.plugin = plugin;
        this.database = database;
        this.worldHandler = worldHandler;
        this.teleportHandler = teleportHandler;
        this.islandSnapshot = islandSnapshot;
        this.islandClaims = islandClaims;
        this.serverID = serverID;
    }

    /**
     * Runs an operation on an island this server hosts. Fails with
     * {@link IslandNotLoadedException} when the island has left this server, so the caller
     * routes it again against the current claim.
     */
    public <T> CompletableFuture<T> asHost(UUID islandUuid, Supplier<CompletableFuture<T>> operation) {
        return serialized(islandUuid, () -> {
            if (!hosted.contains(islandUuid)) {
                return CompletableFuture.failedFuture(new IslandNotLoadedException());
            }

            return operation.get();
        });
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        String islandName = IslandUtils.parseIslandName(islandUuid);

        return serialized(islandUuid, () -> {
            return islandClaims.acquire(islandUuid, islandClaims.hostValue()).thenCompose(host -> {
                if (host != null) {
                    return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
                }

                hosted.add(islandUuid);
                try {
                    database.addIslandData(islandUuid, ownerUuid);
                } catch (Throwable error) {
                    hosted.remove(islandUuid);
                    islandClaims.release(islandUuid, islandClaims.hostValue());
                    return CompletableFuture.failedFuture(error);
                }

                return islandSnapshot.load(islandUuid).thenCompose(v -> {
                    return worldHandler.createWorld(islandName);
                }).thenRun(() -> {
                    plugin.debug("IslandOperator", "Created island " + islandUuid + " on server: " + serverID);
                }).exceptionallyComposeAsync(e -> {
                    return cleanupFailedCreate(islandUuid, islandName).thenCompose(v -> {
                        return CompletableFuture.failedFuture(e);
                    });
                }, plugin.getBukkitAsyncExecutor());
            });
        });
    }

    public CompletableFuture<String> loadIsland(UUID islandUuid) {
        String islandName = IslandUtils.parseIslandName(islandUuid);

        return serialized(islandUuid, () -> islandClaims.acquire(islandUuid, islandClaims.hostValue()).thenCompose(host -> {
            if (host != null) {
                return CompletableFuture.completedFuture(host);
            }

            hosted.add(islandUuid);
            return islandSnapshot.load(islandUuid).thenCompose(v -> worldHandler.loadWorld(islandName)).thenApply(v -> {
                plugin.debug("IslandOperator", "Loaded island " + islandUuid + " on server: " + serverID);
                return serverID;
            }).exceptionallyComposeAsync(e -> {
                hosted.remove(islandUuid);
                islandSnapshot.unload(islandUuid);
                islandClaims.release(islandUuid, islandClaims.hostValue());
                return CompletableFuture.failedFuture(e);
            }, plugin.getBukkitAsyncExecutor());
        }));
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = IslandUtils.parseIslandName(islandUuid);

        return serialized(islandUuid, () -> {
            if (!hosted.contains(islandUuid)) {
                return worldHandler.unloadWorld(islandName).thenRunAsync(() -> {
                    islandClaims.release(islandUuid, islandClaims.hostValue());
                }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> CompletableFuture.failedFuture(new IslandNotLoadedException()));
            }

            return worldHandler.unloadWorld(islandName).thenRun(() -> {
                hosted.remove(islandUuid);
                islandSnapshot.unload(islandUuid);
                islandClaims.release(islandUuid, islandClaims.hostValue());
                plugin.debug("IslandOperator", "Unloaded island " + islandUuid + " and released its claim.");
            });
        });
    }

    /**
     * Deletes the island's rows and world. Run through {@link #asHost} when this server hosts
     * it (the host claim is released here); under a transient claim the caller releases.
     */
    public CompletableFuture<Void> deleteIsland(Actor actor, UUID islandUuid) {
        String islandName = IslandUtils.parseIslandName(islandUuid);

        try {
            database.deleteIsland(actor, islandUuid);
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }

        return worldHandler.deleteWorld(islandName).exceptionally(e -> {
            plugin.severe("Island rows deleted but the world could not be removed, leaving an orphaned world: " + islandName, e);
            return null;
        }).thenRunAsync(() -> {
            if (hosted.remove(islandUuid)) {
                islandSnapshot.unload(islandUuid);
                islandClaims.release(islandUuid, islandClaims.hostValue());
            }
            plugin.debug("IslandOperator", "Deleted island " + islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> prepareTeleport(UUID playerUuid, String teleportWorld, String teleportLocation) {
        UUID islandUuid = IslandUtils.parseIslandUuid(teleportWorld);
        if (islandUuid == null) {
            return teleportOrStore(playerUuid, teleportWorld, teleportLocation);
        }

        return asHost(islandUuid, () -> teleportOrStore(playerUuid, teleportWorld, teleportLocation));
    }

    private CompletableFuture<Boolean> teleportOrStore(UUID playerUuid, String teleportWorld, String teleportLocation) {
        return CompletableFuture.supplyAsync(() -> {
            Location location = LocationUtils.stringToLocation(teleportWorld, teleportLocation);
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.teleportAsync(location);
                plugin.debug("IslandOperator", "Teleported player " + playerUuid + " to location: " + teleportLocation + " in world: " + teleportWorld);
                return true;
            }

            teleportHandler.addPendingTeleport(playerUuid, location);
            plugin.debug("IslandOperator", "Stored pending teleport for player " + playerUuid + " to location: " + teleportLocation + " in world: " + teleportWorld);
            return false;
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }

    public CompletableFuture<Void> expelPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        try {
            Set<UUID> islandPlayers = database.getIslandPlayers(islandUuid).keySet();

            if (actor instanceof Actor.Player player && !islandPlayers.contains(player.uuid())) {
                return CompletableFuture.failedFuture(new IslandDoesNotExistException());
            }

            if (islandPlayers.contains(playerUuid)) {
                return CompletableFuture.failedFuture(new CannotExpelIslandPlayerException());
            }

            return worldHandler.removePlayerFromWorld(IslandUtils.parseIslandName(islandUuid), playerUuid).thenAccept(removed -> {
                if (!removed) {
                    throw new PlayerNotInIslandException();
                }
            });
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    public CompletableFuture<Void> addMember(Actor actor, UUID islandUuid, UUID playerUuid, String role) {
        return updateSnapshot(islandUuid, () -> {
            database.addIslandPlayer(actor, islandUuid, playerUuid, role);
            return null;
        });
    }

    public CompletableFuture<Void> removeMember(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> {
            database.deleteIslandPlayer(actor, islandUuid, playerUuid);
            return null;
        }).thenCompose(v -> {
            return worldHandler.removePlayerFromWorld(IslandUtils.parseIslandName(islandUuid), playerUuid);
        }).thenAccept(removed -> {
        });
    }

    public CompletableFuture<Void> setOwner(Actor actor, UUID islandUuid, UUID newOwnerUuid) {
        return updateSnapshot(islandUuid, () -> {
            database.updateIslandOwner(actor, islandUuid, newOwnerUuid);
            return null;
        });
    }

    public CompletableFuture<Void> addBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> {
            database.updateBanPlayer(actor, islandUuid, playerUuid);
            return null;
        }).thenCompose(v -> {
            return worldHandler.removePlayerFromWorld(IslandUtils.parseIslandName(islandUuid), playerUuid);
        }).thenAccept(removed -> {
        });
    }

    public CompletableFuture<Void> removeBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> {
            database.deleteBanPlayer(actor, islandUuid, playerUuid);
            return null;
        });
    }

    public CompletableFuture<Void> addCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> {
            database.updateCoopPlayer(actor, islandUuid, playerUuid);
            return null;
        });
    }

    public CompletableFuture<Void> removeCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return updateSnapshot(islandUuid, () -> {
            database.deleteCoopPlayer(actor, islandUuid, playerUuid);
            return null;
        }).thenCompose(v -> {
            return worldHandler.removePlayerFromWorld(IslandUtils.parseIslandName(islandUuid), playerUuid);
        }).thenAccept(removed -> {
        });
    }

    public CompletableFuture<Boolean> toggleIslandLock(Actor actor, UUID islandUuid) {
        return updateSnapshot(islandUuid, () -> {
            return database.toggleIslandLock(actor, islandUuid);
        }).thenCompose(locked -> {
            if (!locked) {
                return CompletableFuture.completedFuture(false);
            }

            String islandName = IslandUtils.parseIslandName(islandUuid);
            return plugin.getApi().getIslandPlayers(islandUuid).thenCombine(plugin.getApi().getIslandCoops(islandUuid), (islandPlayers, coops) -> {
                Set<UUID> allowed = new HashSet<>(islandPlayers);
                allowed.addAll(coops);
                return allowed;
            }).thenCompose(allowed -> {
                return worldHandler.removePlayersFromWorld(islandName, player -> !allowed.contains(player.getUniqueId()));
            }).thenApply(v -> true);
        });
    }

    public CompletableFuture<Boolean> toggleIslandPvp(Actor actor, UUID islandUuid) {
        return updateSnapshot(islandUuid, () -> {
            return database.toggleIslandPvp(actor, islandUuid);
        });
    }

    public CompletableFuture<Void> setHome(Actor actor, UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        return updateSnapshot(islandUuid, () -> {
            database.updateHomePoint(actor, islandUuid, playerUuid, homeName, homeLocation);
            return null;
        });
    }

    public CompletableFuture<Void> deleteHome(UUID islandUuid, UUID playerUuid, String homeName) {
        return updateSnapshot(islandUuid, () -> {
            database.deleteHomePoint(islandUuid, playerUuid, homeName);
            return null;
        });
    }

    public CompletableFuture<Void> setWarp(Actor actor, UUID islandUuid, String warpName, String warpLocation) {
        return updateSnapshot(islandUuid, () -> {
            database.updateWarpPoint(actor, islandUuid, warpName, warpLocation);
            return null;
        });
    }

    public CompletableFuture<Void> deleteWarp(Actor actor, UUID islandUuid, String warpName) {
        return updateSnapshot(islandUuid, () -> {
            database.deleteWarpPoint(actor, islandUuid, warpName);
            return null;
        });
    }

    public CompletableFuture<Void> setUpgradeLevel(Actor actor, UUID islandUuid, String upgradeId, int expectedLevel, int newLevel) {
        return updateSnapshot(islandUuid, () -> {
            database.updateIslandUpgradeLevel(actor, islandUuid, upgradeId, expectedLevel, newLevel);
            return null;
        }).thenCompose(v -> {
            if (!upgradeId.equals("island-size")) {
                return CompletableFuture.completedFuture(null);
            }

            return CompletableFuture.runAsync(() -> {
                Island island = islandSnapshot.get(islandUuid);
                if (island == null) {
                    return;
                }
                World world = Bukkit.getWorld(IslandUtils.parseIslandName(islandUuid));
                if (world != null) {
                    int size = island.getSize();
                    WorldBorder border = world.getWorldBorder();
                    border.setCenter(0.0, 0.0);
                    border.setSize(size);
                }
            }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).exceptionally(error -> {
                plugin.severe("Upgrade " + upgradeId + " saved but could not refresh the loaded island: " + islandUuid, error);
                return null;
            });
        });
    }

    private <T> CompletableFuture<T> updateSnapshot(UUID islandUuid, Supplier<T> mutation) {
        try {
            T result = mutation.get();
            return islandSnapshot.reload(islandUuid).thenApply(v -> result);
        } catch (Throwable error) {
            return islandSnapshot.reload(islandUuid).handle((v, reloadError) -> null).thenCompose(v -> CompletableFuture.failedFuture(error));
        }
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

            hosted.remove(islandUuid);
            islandSnapshot.unload(islandUuid);
            islandClaims.release(islandUuid, islandClaims.hostValue());
        }, plugin.getBukkitAsyncExecutor());
    }

    /**
     * Appends the operation to the island's chain. An idle chain runs it inline on the
     * caller's async thread; only a busy chain defers it, and then onto the async executor,
     * because the previous operation may have completed on the main thread (a teleport
     * prepare does). Results are delivered on the operation's own completing thread, as
     * before the chain existed. The chain entry is dropped once idle.
     */
    private <T> CompletableFuture<T> serialized(UUID islandUuid, Supplier<CompletableFuture<T>> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture<Void> previous = chains.put(islandUuid, done);

        Runnable run = () -> supply(operation).whenComplete((value, error) -> {
            done.complete(null);
            chains.remove(islandUuid, done);
            if (error != null) {
                result.completeExceptionally(error);
            } else {
                result.complete(value);
            }
        });

        if (previous == null || previous.isDone()) {
            run.run();
        } else {
            previous.thenRunAsync(run, plugin.getBukkitAsyncExecutor());
        }

        return result;
    }

    private static <T> CompletableFuture<T> supply(Supplier<CompletableFuture<T>> operation) {
        try {
            return operation.get();
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
