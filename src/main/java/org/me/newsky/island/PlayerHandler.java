package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerHandler {


    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public PlayerHandler(NewSky plugin, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return CompletableFuture.runAsync(() -> {
            Optional<UUID> existingIsland = cacheHandler.getIslandUuid(playerUuid);
            if (existingIsland.isPresent()) {
                if (!existingIsland.get().equals(islandUuid)) {
                    throw new IslandAlreadyExistException();
                }
            }

            Set<UUID> members = cacheHandler.getIslandPlayers(islandUuid);
            if (members.contains(playerUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            cacheHandler.updateIslandPlayer(islandUuid, playerUuid, role);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            UUID ownerUuid = cacheHandler.getIslandOwner(islandUuid);
            if (ownerUuid.equals(playerUuid)) {
                throw new CannotRemoveOwnerException();
            }

            Set<UUID> members = cacheHandler.getIslandPlayers(islandUuid);
            if (!members.contains(playerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }

            cacheHandler.deleteIslandPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerId) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> members = cacheHandler.getIslandPlayers(islandUuid);
            if (!members.contains(newOwnerId)) {
                throw new IslandPlayerDoesNotExistException();
            }

            UUID currentOwner = cacheHandler.getIslandOwner(islandUuid);
            if (currentOwner.equals(newOwnerId)) {
                throw new AlreadyOwnerException();
            }

            cacheHandler.updateIslandOwner(islandUuid, newOwnerId);
        }, plugin.getBukkitAsyncExecutor());
    }
}
