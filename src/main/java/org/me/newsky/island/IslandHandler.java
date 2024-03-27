package org.me.newsky.island;

import org.bukkit.Location;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.util.LocationUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles island operations
 */
public class IslandHandler {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;

    public IslandHandler(ConfigHandler configHandler, CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.config = configHandler;
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
    }

    /**
     * Utility Methods
     */
    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid, boolean checkExistence) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandUuid(playerUuid)).thenApply(islandUuidOpt -> {
            if (checkExistence && islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            return islandUuidOpt.orElseThrow(IslandAlreadyExistException::new);
        });
    }

    public CompletableFuture<Set<UUID>> getIslandPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getIslandPlayers(islandUuid));
    }


    /**
     * Island Operation Related Methods
     */
    public CompletableFuture<Void> createIsland(UUID playerUuid) {
        return getIslandUuid(playerUuid, false).thenCompose(islandUuid -> {
            UUID newIslandUuid = UUID.randomUUID();
            String spawnLocation = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

            cacheHandler.createIsland(newIslandUuid);
            cacheHandler.updateIslandPlayer(newIslandUuid, playerUuid, "owner");
            cacheHandler.updateHomePoint(newIslandUuid, playerUuid, "default", spawnLocation);

            return preIslandHandler.createIsland(newIslandUuid);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID playerUuid) {
        return getIslandUuid(playerUuid, true).thenCompose(islandUuid -> {
            cacheHandler.deleteIsland(islandUuid);
            return preIslandHandler.deleteIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID playerUuid) {
        return getIslandUuid(playerUuid, true).thenCompose(preIslandHandler::loadIsland);
    }

    public CompletableFuture<Void> unloadIsland(UUID playerUuid) {
        return getIslandUuid(playerUuid, true).thenCompose(preIslandHandler::unloadIsland);
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID playerUuid) {
        return getIslandUuid(playerUuid, true).thenCompose(islandUuid -> {
            boolean isLocked = cacheHandler.getIslandLock(islandUuid);
            cacheHandler.updateIslandLock(islandUuid, !isLocked);
            if (!isLocked) {
                preIslandHandler.lockIsland(islandUuid);
            }
            return CompletableFuture.completedFuture(!isLocked);
        });
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID playerUuid) {
        return getIslandUuid(playerUuid, true).thenCompose(islandUuid -> {
            boolean isPvpEnabled = cacheHandler.getIslandPvp(islandUuid);
            cacheHandler.updateIslandPvp(islandUuid, !isPvpEnabled);
            return CompletableFuture.completedFuture(!isPvpEnabled);
        });
    }

    /**
     * Island Player Related Methods
     */
    public CompletableFuture<Void> addMember(UUID islandOwnerId, UUID playerUuid, String role) {
        return getIslandUuid(islandOwnerId, true).thenCompose(islandUuid -> getIslandPlayers(islandUuid).thenAccept(members -> {
            if (members.contains(playerUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }
            cacheHandler.updateIslandPlayer(islandUuid, playerUuid, role);
        }));
    }

    public CompletableFuture<Void> removeMember(UUID islandOwnerId, UUID playerUuid) {
        return getIslandUuid(islandOwnerId, true).thenCompose(islandUuid -> getIslandPlayers(islandUuid).thenAccept(members -> {
            if (islandOwnerId.equals(playerUuid)) {
                throw new CannotRemoveOwnerException();
            }
            if (!members.contains(playerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }
            cacheHandler.deleteIslandPlayer(islandUuid, playerUuid);
        }));
    }

    public CompletableFuture<Void> setOwner(UUID islandOwnerId, UUID newOwnerId) {
        return getIslandUuid(islandOwnerId, true).thenCompose(islandUuid -> getIslandPlayers(islandUuid).thenAccept(members -> {
            Optional<UUID> ownerUuidOpt = cacheHandler.getIslandOwner(islandUuid);
            if (ownerUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID ownerUuid = ownerUuidOpt.get();
            if (ownerUuid.equals(newOwnerId)) {
                throw new AlreadyOwnerException();
            }
            if (!members.contains(newOwnerId)) {
                throw new IslandPlayerDoesNotExistException();
            }
            cacheHandler.updateIslandOwner(islandUuid, newOwnerId);
        }));
    }

    /**
     * Home Related Methods
     */
    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, Location location) {
        return getIslandUuid(playerUuid, true).thenAccept(islandUuid -> {
            if (location.getWorld() == null || !location.getWorld().getName().equals("island-" + islandUuid)) {
                throw new LocationNotInIslandException();
            }
            String homeLocation = LocationUtils.locationToString(location);
            cacheHandler.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);
        });
    }

    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return getIslandUuid(playerUuid, true).thenAccept(islandUuid -> {
            if (cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName).isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            cacheHandler.deleteHomePoint(islandUuid, playerUuid, homeName);
        });
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName) {
        return getIslandUuid(playerUuid, true).thenCompose(islandUuid -> {
            Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName);
            if (homeLocationOpt.isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            String homeLocation = homeLocationOpt.get();
            return preIslandHandler.teleportToIsland(playerUuid, islandUuid, homeLocation);
        });
    }

    public CompletableFuture<Set<String>> getHomeNames(UUID playerUuid) {
        return getIslandUuid(playerUuid, true).thenApply(islandUuid -> cacheHandler.getHomeNames(islandUuid, playerUuid));
    }

    /**
     * Warp Related Methods
     */
    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, Location location) {
        return getIslandUuid(playerUuid, true).thenAccept(islandUuid -> {
            if (location.getWorld() == null || !location.getWorld().getName().equals("island-" + islandUuid)) {
                throw new LocationNotInIslandException();
            }
            String warpLocation = LocationUtils.locationToString(location);
            cacheHandler.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);
        });
    }

    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return getIslandUuid(playerUuid, true).thenAccept(islandUuid -> {
            if (cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName).isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            cacheHandler.deleteWarpPoint(islandUuid, playerUuid, warpName);
        });
    }

    public CompletableFuture<Void> warp(UUID playerUuid, String warpName) {
        return getIslandUuid(playerUuid, true).thenCompose(islandUuid -> {
            Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName);
            if (warpLocationOpt.isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            String warpLocation = warpLocationOpt.get();
            return preIslandHandler.teleportToIsland(playerUuid, islandUuid, warpLocation);
        });
    }

    public CompletableFuture<Set<String>> getWarpNames(UUID playerUuid) {
        return getIslandUuid(playerUuid, true).thenApply(islandUuid -> cacheHandler.getWarpNames(islandUuid, playerUuid));
    }
}

