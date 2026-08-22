package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cluster.InvitationStore;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Invitation;
import org.me.newsky.network.IslandDistributor;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class PlayerHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;
    private final InvitationStore invitationStore;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public PlayerHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor,
                         InvitationStore invitationStore, OnlinePlayerRegistry onlinePlayerRegistry) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
        this.invitationStore = invitationStore;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    public CompletableFuture<Void> addMember(Actor actor, UUID islandUuid, UUID playerUuid,
                                             String role, UUID vouchedBy) {
        actor.requireSelf(playerUuid);
        return islandDistributor.addMember(islandUuid, playerUuid, role, vouchedBy);
    }

    public CompletableFuture<Void> removeMember(Actor actor, UUID islandUuid, UUID playerUuid) {
        return islandDistributor.removeMember(islandUuid, actor, playerUuid);
    }

    public CompletableFuture<Void> setOwner(Actor actor, UUID islandUuid, UUID newOwnerUuid) {
        return islandDistributor.setOwner(islandUuid, actor, newOwnerUuid);
    }

    public CompletableFuture<Void> expelPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            onlinePlayerRegistry.requireOnline(playerUuid);

            Set<UUID> islandPlayers = database.getIslandPlayers(islandUuid).keySet();

            if (actor instanceof Actor.Player player && !islandPlayers.contains(player.uuid())) {
                throw new IslandDoesNotExistException();
            }

            if (islandPlayers.contains(playerUuid)) {
                throw new CannotExpelIslandPlayerException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> {
            return islandDistributor.expelPlayer(islandUuid, actor, playerUuid);
        });
    }

    public CompletableFuture<Void> addInvite(Actor actor, UUID islandUuid, UUID inviteeUuid, int ttlSeconds) {
        if (!(actor instanceof Actor.Player inviter)) {
            return CompletableFuture.failedFuture(new ActorNotAuthorizedException());
        }

        return CompletableFuture.runAsync(() -> {
            onlinePlayerRegistry.requireOnline(inviteeUuid);

            Set<UUID> members = database.getIslandPlayers(islandUuid).keySet();
            if (members.contains(inviteeUuid)) {
                throw new IslandPlayerAlreadyExistsException();
            }

            Optional<UUID> existingIsland = database.getIslandUuid(inviteeUuid);
            if (existingIsland.isPresent() && !existingIsland.get().equals(islandUuid)) {
                throw new IslandAlreadyExistException();
            }

            if (!invitationStore.addInvite(inviteeUuid, islandUuid, inviter.uuid(), ttlSeconds)) {
                throw new InvitedAlreadyException();
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> removeInvite(Actor actor, UUID inviteeUuid) {
        actor.requireSelf(inviteeUuid);
        return CompletableFuture.runAsync(() -> invitationStore.removeInvite(inviteeUuid),
                plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<Invitation>> getInvite(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> invitationStore.getInvite(playerUuid),
                plugin.getBukkitAsyncExecutor());
    }

    private static final int REINSTATED_INVITE_TTL_SECONDS = 300;

    public CompletableFuture<Optional<Invitation>> acceptInvite(Actor actor, UUID inviteeUuid) {
        actor.requireSelf(inviteeUuid);

        return CompletableFuture.supplyAsync(() -> invitationStore.consumeInvite(inviteeUuid),
                plugin.getBukkitAsyncExecutor()).thenCompose(invite -> {
            if (invite.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            return acceptConsumedInvite(actor, inviteeUuid, invite.get());
        });
    }

    private CompletableFuture<Optional<Invitation>> acceptConsumedInvite(
            Actor actor, UUID inviteeUuid, Invitation invitation) {
        UUID islandUuid = invitation.getIslandUuid();
        UUID inviterUuid = invitation.getInviterUuid();

        return addMember(actor, islandUuid, inviteeUuid, "member", inviterUuid)
                .thenApply(v -> Optional.of(invitation))
                .exceptionallyCompose(error -> {
                    return recoverFailedAccept(inviteeUuid, invitation, error);
                });
    }

    private CompletableFuture<Optional<Invitation>> recoverFailedAccept(
            UUID inviteeUuid, Invitation invitation, Throwable error) {
        UUID islandUuid = invitation.getIslandUuid();

        return CompletableFuture.supplyAsync(() -> database.getIslandUuid(inviteeUuid),
                plugin.getBukkitAsyncExecutor()).thenCompose(currentIsland -> {
            if (currentIsland.filter(islandUuid::equals).isPresent()) {
                plugin.warning("Invitation response failed, but membership committed for " + inviteeUuid);
                return CompletableFuture.completedFuture(Optional.of(invitation));
            }

            Throwable cause = unwrap(error);
            if (shouldReinstateInvite(currentIsland, cause)) {
                reinstateInvite(inviteeUuid, invitation);
            } else if (cause instanceof TimeoutException) {
                plugin.warning("Not reinstating invitation after an ambiguous timeout for " + inviteeUuid);
            }

            return CompletableFuture.failedFuture(error);
        });
    }

    private boolean shouldReinstateInvite(Optional<UUID> currentIsland, Throwable cause) {
        return currentIsland.isEmpty()
                && !(cause instanceof InviterNotMemberException)
                && !(cause instanceof TimeoutException);
    }

    private void reinstateInvite(UUID inviteeUuid, Invitation invitation) {
        try {
            invitationStore.addInvite(inviteeUuid, invitation.getIslandUuid(),
                    invitation.getInviterUuid(), REINSTATED_INVITE_TTL_SECONDS);
        } catch (Exception error) {
            plugin.severe("Failed to reinstate invitation for " + inviteeUuid, error);
        }
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return database.getIslandCore(islandUuid)
                    .flatMap(DatabaseHandler.IslandCoreData::owner)
                    .orElseThrow(IslandDoesNotExistException::new);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> members = new HashSet<>();
            for (Map.Entry<UUID, String> entry : database.getIslandPlayers(islandUuid).entrySet()) {
                if ("member".equalsIgnoreCase(entry.getValue())) {
                    members.add(entry.getKey());
                }
            }
            return members;
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).keySet(),
                plugin.getBukkitAsyncExecutor());
    }
}
