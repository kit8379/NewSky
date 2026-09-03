package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cluster.InvitationStore;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Invitation;
import org.me.newsky.network.IslandDistributor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PlayerHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;
    private final InvitationStore invitationStore;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public PlayerHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor, InvitationStore invitationStore, OnlinePlayerRegistry onlinePlayerRegistry) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
        this.invitationStore = invitationStore;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.addMember(islandUuid, playerUuid, role), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> removeMember(Actor actor, UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.removeMember(actor, islandUuid, playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> setOwner(Actor actor, UUID islandUuid, UUID newOwnerUuid) {
        return CompletableFuture.completedFuture(null).thenComposeAsync(v -> islandDistributor.setOwner(actor, islandUuid, newOwnerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> expelPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!onlinePlayerRegistry.isOnline(playerUuid)) {
                throw new PlayerNotOnlineException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandDistributor.expelPlayer(actor, islandUuid, playerUuid);
        });
    }

    public CompletableFuture<Void> addPendingInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        return CompletableFuture.runAsync(() -> {
            if (!onlinePlayerRegistry.isOnline(inviteeUuid)) {
                throw new PlayerNotOnlineException();
            }

            Set<UUID> members = database.getIslandPlayers(islandUuid).keySet();
            if (members.contains(inviteeUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            Optional<UUID> existingIsland = database.getIslandUuid(inviteeUuid);
            if (existingIsland.isPresent() && !existingIsland.get().equals(islandUuid)) {
                throw new IslandAlreadyExistException();
            }

            Optional<Invitation> existingInvite = invitationStore.getIslandInvite(inviteeUuid);
            if (existingInvite.isPresent()) {
                throw new InvitedAlreadyException();
            }

            invitationStore.addIslandInvite(inviteeUuid, islandUuid, inviterUuid, ttlSeconds);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> removePendingInvite(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> invitationStore.removeIslandInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<Invitation>> getPendingInvite(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> invitationStore.getIslandInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandOwner(islandUuid).orElseThrow(IslandDoesNotExistException::new), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).entrySet().stream().filter(entry -> "member".equalsIgnoreCase(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet()), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).keySet(), plugin.getBukkitAsyncExecutor());
    }
}
