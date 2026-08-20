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
    public CompletableFuture<Void> addMember(Actor actor, UUID islandUuid, UUID playerUuid, String role) {
        actor.requireSelf(playerUuid);
        return islandDistributor.addMember(islandUuid, playerUuid, role);
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
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.expelPlayer(islandUuid, playerUuid));
    }

    /**
     * SELF: an invitation is a personal vouch, so it is recorded against the actor who issued it.
     * A Bypass has no player identity and therefore cannot invite - console and operators add
     * members directly with {@link #addMember} instead.
     */
    public CompletableFuture<Void> addPendingInvite(Actor actor, UUID islandUuid, UUID inviteeUuid, int ttlSeconds) {
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

            if (!invitationStore.addIslandInvite(inviteeUuid, islandUuid, inviter.uuid(), ttlSeconds)) {
                throw new InvitedAlreadyException();
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    /** SELF: a player may only discard their own pending invitation. */
    public CompletableFuture<Void> removePendingInvite(Actor actor, UUID inviteeUuid) {
        actor.requireSelf(inviteeUuid);
        return CompletableFuture.runAsync(() -> invitationStore.removeIslandInvite(inviteeUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<Invitation>> getPendingInvite(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> invitationStore.getIslandInvite(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    /** SELF: a player may only redeem their own invitation. */
    public CompletableFuture<Optional<Invitation>> consumePendingInvite(Actor actor, UUID inviteeUuid) {
        actor.requireSelf(inviteeUuid);
        return CompletableFuture.supplyAsync(() -> invitationStore.consumeIslandInvite(inviteeUuid), plugin.getBukkitAsyncExecutor());
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
