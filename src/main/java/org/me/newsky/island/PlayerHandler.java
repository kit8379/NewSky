package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Invitation;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerHandler {

    private final NewSky plugin;
    private final Cache cache;
    private final RedisCache redisCache;

    public PlayerHandler(NewSky plugin, Cache cache, RedisCache redisCache) {
        this.plugin = plugin;
        this.cache = cache;
        this.redisCache = redisCache;
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return CompletableFuture.runAsync(() -> {
            Optional<UUID> existingIsland = cache.getIslandUuid(playerUuid);
            if (existingIsland.isPresent()) {
                if (!existingIsland.get().equals(islandUuid)) {
                    throw new IslandAlreadyExistException();
                }
            }

            Set<UUID> members = cache.getIslandPlayers(islandUuid);
            if (members.contains(playerUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            UUID ownerUuid = cache.getIslandOwner(islandUuid);
            cache.updateIslandPlayer(islandUuid, playerUuid, role);
            Optional<String> homePoint = cache.getHomeLocation(islandUuid, ownerUuid, "default");
            homePoint.ifPresent(s -> cache.updateHomePoint(islandUuid, playerUuid, "default", s));
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            UUID ownerUuid = cache.getIslandOwner(islandUuid);
            if (ownerUuid.equals(playerUuid)) {
                throw new CannotRemoveOwnerException();
            }

            Set<UUID> members = cache.getIslandPlayers(islandUuid);
            if (!members.contains(playerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }

            cache.deleteIslandPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerId) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> members = cache.getIslandPlayers(islandUuid);
            if (!members.contains(newOwnerId)) {
                throw new IslandPlayerDoesNotExistException();
            }

            UUID currentOwner = cache.getIslandOwner(islandUuid);
            if (currentOwner.equals(newOwnerId)) {
                throw new AlreadyOwnerException();
            }

            cache.updateIslandOwner(islandUuid, newOwnerId);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> addPendingInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        return CompletableFuture.runAsync(() -> {
            if (getPendingInvite(inviteeUuid).isPresent()) {
                throw new InvitedAlreadyException();
            }

            Optional<UUID> existingIsland = cache.getIslandUuid(inviteeUuid);
            if (existingIsland.isPresent()) {
                if (!existingIsland.get().equals(islandUuid)) {
                    throw new IslandAlreadyExistException();
                }
            }

            Set<UUID> members = cache.getIslandPlayers(islandUuid);
            if (members.contains(inviteeUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            redisCache.addIslandInvite(inviteeUuid, islandUuid, inviterUuid, ttlSeconds);
        }, plugin.getBukkitAsyncExecutor());
    }


    public CompletableFuture<Void> removePendingInvite(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> redisCache.removeIslandInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }


    public Optional<Invitation> getPendingInvite(UUID playerUuid) {
        return redisCache.getIslandInvite(playerUuid);
    }
}
