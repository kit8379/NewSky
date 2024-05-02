package org.me.newsky.island;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerHandler {

    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public PlayerHandler(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
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
}
