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

    /**
     * SELF: a player joins an island for themselves, having accepted its invitation; admins add
     * anyone with a Bypass. There is deliberately no island role rule here - the joiner is not a
     * member yet, so the authorization is the invitation, consumed by the accept command before
     * this call. Conflict checks and home seeding happen inside the insert transaction.
     */
    public CompletableFuture<Void> addMember(Actor actor, UUID islandUuid, UUID playerUuid, String role, UUID vouchedBy) {
        actor.requireSelf(playerUuid);
        return islandDistributor.addMember(islandUuid, playerUuid, role, vouchedBy);
    }

    /** MEMBER, enforced in the delete transaction. */
    public CompletableFuture<Void> removeMember(Actor actor, UUID islandUuid, UUID playerUuid) {
        return islandDistributor.removeMember(islandUuid, actor, playerUuid);
    }

    /** OWNER, enforced in the transfer transaction. */
    public CompletableFuture<Void> setOwner(Actor actor, UUID islandUuid, UUID newOwnerUuid) {
        return islandDistributor.setOwner(islandUuid, actor, newOwnerUuid);
    }

    /** MEMBER, enforced again on the island's host at the moment of the kick. */
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
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.expelPlayer(islandUuid, actor, playerUuid));
    }

    /**
     * SELF: an invitation is a personal vouch, so it is recorded against the actor who issued it.
     * A Bypass has no player identity and therefore cannot invite - console and operators add
     * members directly with {@link #addMember} instead.
     */
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
        return CompletableFuture.runAsync(() -> invitationStore.removeInvite(inviteeUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<Invitation>> getInvite(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> invitationStore.getInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    // A join that fails after the invitation was consumed puts it back (best effort, fresh TTL),
    // so a transient failure does not burn the invite. The one exception is a dead voucher: an
    // invitation whose issuer is no longer a member is genuinely void.
    private static final int REINSTATED_INVITE_TTL_SECONDS = 300;

    public CompletableFuture<Optional<Invitation>> acceptInvite(Actor actor, UUID inviteeUuid) {
        actor.requireSelf(inviteeUuid);

        return CompletableFuture.supplyAsync(() -> invitationStore.consumeInvite(inviteeUuid), plugin.getBukkitAsyncExecutor()).thenCompose(invite -> {
            if (invite.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.<Invitation>empty());
            }

            UUID islandUuid = invite.get().getIslandUuid();
            UUID inviterUuid = invite.get().getInviterUuid();

            // The inviter's membership is re-verified inside the add-member transaction, under
            // the island lock - an invitation is a vouch and dies with the voucher's membership.
            return addMember(actor, islandUuid, inviteeUuid, "member", inviterUuid).thenApply(v -> invite).exceptionallyCompose(error -> {
                if (!(unwrap(error) instanceof InviterNotMemberException)) {
                    try {
                        invitationStore.addInvite(inviteeUuid, islandUuid, inviterUuid, REINSTATED_INVITE_TTL_SECONDS);
                    } catch (Exception reinstateFailure) {
                        plugin.severe("Failed to reinstate invitation for " + inviteeUuid + " after a failed accept", reinstateFailure);
                    }
                }
                return CompletableFuture.failedFuture(error);
            });
        });
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandCore(islandUuid).flatMap(DatabaseHandler.IslandCoreData::owner).orElseThrow(IslandDoesNotExistException::new), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).entrySet().stream().filter(entry -> "member".equalsIgnoreCase(entry.getValue())).map(Map.Entry::getKey).collect(Collectors.toSet()), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getIslandPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandPlayers(islandUuid).keySet(), plugin.getBukkitAsyncExecutor());
    }
}
