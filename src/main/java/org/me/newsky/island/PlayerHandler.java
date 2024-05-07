package org.me.newsky.island;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.AlreadyOwnerException;
import org.me.newsky.exceptions.IslandPlayerAlreadyExistsException;
import org.me.newsky.exceptions.IslandPlayerDoesNotExistException;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerHandler {

    private final CacheHandler cacheHandler;

    public PlayerHandler(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> members = cacheHandler.getIslandPlayers(islandUuid);
            if (members.contains(playerUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            cacheHandler.updateIslandPlayer(islandUuid, playerUuid, role);
        });
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> members = cacheHandler.getIslandPlayers(islandUuid);
            if (!members.contains(playerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }

            cacheHandler.deleteIslandPlayer(islandUuid, playerUuid);
        });
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerId) {
        return CompletableFuture.runAsync(() -> {
            UUID currentOwner = cacheHandler.getIslandOwner(islandUuid);
            if (currentOwner.equals(newOwnerId)) {
                throw new AlreadyOwnerException();
            }

            // Set the new owner
            cacheHandler.updateIslandOwner(islandUuid, newOwnerId);
        });
    }
}
