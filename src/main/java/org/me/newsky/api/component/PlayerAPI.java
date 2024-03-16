package org.me.newsky.api.component;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.CannotRemoveOwnerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.MemberAlreadyExistsException;
import org.me.newsky.exceptions.MemberDoesNotExistException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerAPI {

    private final CacheHandler cacheHandler;

    public PlayerAPI(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    public CompletableFuture<Void> addMember(UUID islandOwnerId, UUID targetUuid, String role) {
        return CompletableFuture.runAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            Set<UUID> members = cacheHandler.getIslandMembers(islandUuid);
            if (members.contains(targetUuid)) {
                throw new MemberAlreadyExistsException();
            }

            cacheHandler.updateIslandPlayer(targetUuid, islandUuid, role);
        });
    }

    public CompletableFuture<Void> removeMember(UUID islandOwnerId, UUID targetUuid) {
        return CompletableFuture.runAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            if (islandOwnerId.equals(targetUuid)) {
                throw new CannotRemoveOwnerException();
            }

            Set<UUID> members = cacheHandler.getIslandMembers(islandUuid);
            if (!members.contains(targetUuid)) {
                throw new MemberDoesNotExistException();
            }

            cacheHandler.deleteIslandPlayer(targetUuid, islandUuid);
        });
    }

    public CompletableFuture<Void> setOwner(UUID islandOwnerId, UUID newOwnerId) {
        return CompletableFuture.runAsync(() -> {
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }
            UUID islandUuid = islandUuidOpt.get();

            Set<UUID> members = cacheHandler.getIslandMembers(islandUuid);
            if (!members.contains(newOwnerId)) {
                throw new MemberDoesNotExistException();
            }

            cacheHandler.updateIslandOwner(newOwnerId, islandUuid);
        });
    }
}