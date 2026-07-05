package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Invitation;
import org.me.newsky.network.IslandDistributor;
import org.me.newsky.state.IslandInvitationState;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final DataCache dataCache;
    private final IslandDistributor islandDistributor;
    private final IslandInvitationState islandInvitationState;

    public PlayerHandler(NewSky plugin, ConfigHandler config, DataCache dataCache, IslandDistributor islandDistributor, IslandInvitationState islandInvitationState) {
        this.plugin = plugin;
        this.config = config;
        this.dataCache = dataCache;
        this.islandDistributor = islandDistributor;
        this.islandInvitationState = islandInvitationState;
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UUID> existingIsland = dataCache.getIslandUuid(playerUuid);
            if (existingIsland.isPresent() && !existingIsland.get().equals(islandUuid)) {
                throw new IslandAlreadyExistException();
            }

            Set<UUID> members = dataCache.getIslandPlayers(islandUuid);
            if (members.contains(playerUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            UUID ownerUuid = dataCache.getIslandOwner(islandUuid);

            return dataCache.getHomeLocation(islandUuid, ownerUuid, "default").orElse(config.getIslandSpawnX() + "," + config.getIslandSpawnY() + "," + config.getIslandSpawnZ() + "," + config.getIslandSpawnYaw() + "," + config.getIslandSpawnPitch());
        }, plugin.getBukkitAsyncExecutor()).thenCompose(homeLocation -> islandDistributor.addMember(islandUuid, playerUuid, role, homeLocation));
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            UUID ownerUuid = dataCache.getIslandOwner(islandUuid);
            if (ownerUuid.equals(playerUuid)) {
                throw new CannotRemoveOwnerException();
            }

            Set<UUID> members = dataCache.getIslandPlayers(islandUuid);
            if (!members.contains(playerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.removeMember(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> members = dataCache.getIslandPlayers(islandUuid);
            if (!members.contains(newOwnerUuid)) {
                throw new IslandPlayerDoesNotExistException();
            }

            UUID oldOwnerUuid = dataCache.getIslandOwner(islandUuid);
            if (oldOwnerUuid.equals(newOwnerUuid)) {
                throw new PlayerAlreadyOwnerException();
            }

            return oldOwnerUuid;
        }, plugin.getBukkitAsyncExecutor()).thenCompose(oldOwnerUuid -> islandDistributor.setOwner(islandUuid, oldOwnerUuid, newOwnerUuid));
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> players = dataCache.getIslandPlayers(islandUuid);
            if (players.contains(playerUuid)) {
                throw new CannotExpelIslandPlayerException();
            }

        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.expelPlayer(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> addPendingInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> members = dataCache.getIslandPlayers(islandUuid);
            if (members.contains(inviteeUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            Optional<UUID> existingIsland = dataCache.getIslandUuid(inviteeUuid);
            if (existingIsland.isPresent() && !existingIsland.get().equals(islandUuid)) {
                throw new IslandAlreadyExistException();
            }

            Optional<Invitation> existingInvite = islandInvitationState.getIslandInvite(inviteeUuid);
            if (existingInvite.isPresent()) {
                throw new InvitedAlreadyException();
            }

            islandInvitationState.addIslandInvite(inviteeUuid, islandUuid, inviterUuid, ttlSeconds);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> removePendingInvite(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> islandInvitationState.removeIslandInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<Invitation>> getPendingInvite(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> islandInvitationState.getIslandInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getIslandOwner(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getIslandMembers(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getIslandPlayers(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}
