package org.me.newsky.island;

import org.bukkit.Location;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.module.LevelCalculation;
import org.me.newsky.util.LocationUtils;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;
    private final LevelCalculation levelCalculation;

    public IslandHandler(ConfigHandler configHandler, CacheHandler cacheHandler, PreIslandHandler preIslandHandler, LevelCalculation levelCalculation) {
        this.config = configHandler;
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
        this.levelCalculation = levelCalculation;
    }

    public CompletableFuture<Void> createIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {

            // Check if the player already has an island
            if (islandUuidOpt.isPresent()) {
                throw new IslandAlreadyExistException();
            }

            // Create a new island using a random UUID
            UUID islandUuid = UUID.randomUUID();
            String spawnLocation = config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch();

            cacheHandler.createIsland(islandUuid);
            cacheHandler.updateIslandPlayer(islandUuid, playerUuid, "owner");
            cacheHandler.updateHomePoint(islandUuid, playerUuid, "default", spawnLocation);

            return preIslandHandler.createIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {

            // Check if the player has an island
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            cacheHandler.deleteIsland(islandUuid);

            return preIslandHandler.deleteIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {

            // Check if the player has an island
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            return preIslandHandler.loadIsland(islandUuid);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {

            // Check if the player has an island
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            return preIslandHandler.unloadIsland(islandUuid);
        });
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(playerUuid);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            boolean isLocked = cacheHandler.getIslandLock(islandUuid);

            if (!isLocked) {
                cacheHandler.updateIslandLock(islandUuid, true);
                preIslandHandler.lockIsland(islandUuid);
            } else {
                cacheHandler.updateIslandLock(islandUuid, false);
            }

            return !isLocked;
        });
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(playerUuid);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            boolean isPvpEnabled = cacheHandler.getIslandPvp(islandUuid);
            cacheHandler.updateIslandPvp(islandUuid, !isPvpEnabled);
            return !isPvpEnabled;
        });
    }

    public CompletableFuture<Void> addMember(UUID islandOwnerId, UUID playerUuid, String role) {
        return CompletableFuture.runAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(islandOwnerId);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            Set<UUID> members = cacheHandler.getIslandPlayers(islandUuid);
            if (members.contains(playerUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            cacheHandler.updateIslandPlayer(islandUuid, playerUuid, role);
        });
    }

    public CompletableFuture<Void> removeMember(UUID islandOwnerId, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(islandOwnerId);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            if (islandOwnerId.equals(playerUuid)) {
                throw new CannotRemoveOwnerException();
            }

            Set<UUID> members = cacheHandler.getIslandPlayers(islandUuid);
            if (!members.contains(playerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }

            cacheHandler.deleteIslandPlayer(islandUuid, playerUuid);
        });
    }

    public CompletableFuture<Void> setOwner(UUID islandOwnerId, UUID newOwnerId) {
        return CompletableFuture.runAsync(() -> {
            // Get the island UUID
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuid(islandOwnerId);

            // Check if the island exists
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            // Get the island UUID
            UUID islandUuid = islandUuidOpt.get();

            // Get the owner UUID
            Optional<UUID> ownerUuidOpt = cacheHandler.getIslandOwner(islandUuid);
            if (ownerUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID ownerUuid = ownerUuidOpt.get();

            // Check if the new owner is already the owner
            if (ownerUuid.equals(newOwnerId)) {
                throw new AlreadyOwnerException();
            }

            // Get the members of the island
            Set<UUID> members = cacheHandler.getIslandPlayers(islandUuid);

            // Check if the new owner is a member of the island
            if (!members.contains(newOwnerId)) {
                throw new IslandPlayerDoesNotExistException();
            }

            // Set the new owner
            cacheHandler.updateIslandOwner(islandUuid, newOwnerId);
        });
    }

    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (location.getWorld() == null || !location.getWorld().getName().equals("island-" + islandUuid)) {
                throw new LocationNotInIslandException();
            }
            String homeLocation = LocationUtils.locationToString(location);
            cacheHandler.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName).isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            cacheHandler.deleteHomePoint(islandUuid, playerUuid, homeName);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(islandUuid, playerUuid, homeName);
            if (homeLocationOpt.isEmpty()) {
                throw new HomeDoesNotExistException();
            }
            String homeLocation = homeLocationOpt.get();
            return preIslandHandler.teleportToIsland(playerUuid, islandUuid, homeLocation);
        });
    }

    public CompletableFuture<Set<String>> getHomeNames(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenApply(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            return cacheHandler.getHomeNames(islandUuid, playerUuid);
        });
    }

    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (location.getWorld() == null || !location.getWorld().getName().equals("island-" + islandUuid)) {
                throw new LocationNotInIslandException();
            }
            String warpLocation = LocationUtils.locationToString(location);
            cacheHandler.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            if (cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName).isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            cacheHandler.deleteWarpPoint(islandUuid, playerUuid, warpName);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> warp(UUID playerUuid, String warpName) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(islandUuid, playerUuid, warpName);
            if (warpLocationOpt.isEmpty()) {
                throw new WarpDoesNotExistException();
            }
            String warpLocation = warpLocationOpt.get();
            return preIslandHandler.teleportToIsland(playerUuid, islandUuid, warpLocation);
        });
    }

    public CompletableFuture<Set<String>> getWarpNames(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandUuid(playerUuid);
        }).thenApply(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();
            return cacheHandler.getWarpNames(islandUuid, playerUuid);
        });
    }

    public CompletableFuture<Void> calculateIslandLevel(UUID islandUuid) {
        return levelCalculation.calculateIslandLevel(islandUuid);
    }

    public CompletableFuture<Integer> getIslandLevel(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return cacheHandler.getIslandLevel(islandUuid);
        });
    }
}