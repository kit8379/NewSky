package org.me.newsky.api;

import org.me.newsky.island.BanHandler;
import org.me.newsky.island.BiomeHandler;
import org.me.newsky.island.CoopHandler;
import org.me.newsky.island.CoreHandler;
import org.me.newsky.island.HomeHandler;
import org.me.newsky.island.PlayerHandler;
import org.me.newsky.island.WarpHandler;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Invitation;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Everything a player may do, scoped to that player. Obtained via {@code api.player(uuid)}.
 * <p>
 * Two guarantees are structural here, not runtime checks: a player can only act as themselves
 * (there is no parameter to name anyone else as the actor or the subject), and island-scoped
 * operations always target <b>their own island</b> - resolved internally, because a player has
 * exactly one. Operator-only operations simply do not exist on this handle.
 * <p>
 * Island roles (OWNER for delete/transfer, MEMBER for the rest) are still enforced where they
 * must be: inside the write transaction, under the island row lock.
 */
public final class PlayerActions {

    private final UUID playerUuid;
    private final Actor actor;
    private final CoreHandler coreHandler;
    private final PlayerHandler playerHandler;
    private final HomeHandler homeHandler;
    private final WarpHandler warpHandler;
    private final BanHandler banHandler;
    private final CoopHandler coopHandler;
    private final BiomeHandler biomeHandler;

    PlayerActions(UUID playerUuid, CoreHandler coreHandler, PlayerHandler playerHandler, HomeHandler homeHandler, WarpHandler warpHandler, BanHandler banHandler, CoopHandler coopHandler, BiomeHandler biomeHandler) {
        this.playerUuid = playerUuid;
        this.actor = new Actor.Player(playerUuid);
        this.coreHandler = coreHandler;
        this.playerHandler = playerHandler;
        this.homeHandler = homeHandler;
        this.warpHandler = warpHandler;
        this.banHandler = banHandler;
        this.coopHandler = coopHandler;
        this.biomeHandler = biomeHandler;
    }

    /**
     * The island every island-scoped write on this handle targets. Resolved here, right before
     * the write, rather than by the caller: fresher than a pre-resolved uuid, and it makes
     * "another island" unrepresentable. Fails with IslandDoesNotExistException when the player
     * has none.
     */
    private CompletableFuture<UUID> ownIsland() {
        return coreHandler.getIslandUuid(playerUuid);
    }

    // ---- island lifecycle -------------------------------------------------------------------

    public CompletableFuture<Void> createIsland() {
        return coreHandler.createIsland(actor, playerUuid);
    }

    /** OWNER, enforced in the delete transaction. */
    public CompletableFuture<Void> deleteIsland() {
        return ownIsland().thenCompose(islandUuid -> coreHandler.deleteIsland(actor, islandUuid));
    }

    /** Leave the island. Owners cannot leave (CannotRemoveOwnerException); they transfer or delete. */
    public CompletableFuture<Void> leave() {
        return ownIsland().thenCompose(islandUuid -> playerHandler.removeMember(actor, islandUuid, playerUuid));
    }

    // ---- membership and trust ---------------------------------------------------------------

    public CompletableFuture<Void> invite(UUID inviteeUuid, int ttlSeconds) {
        return ownIsland().thenCompose(islandUuid -> playerHandler.addInvite(actor, islandUuid, inviteeUuid, ttlSeconds));
    }

    /**
     * Atomically redeems this player's pending invitation and joins the island it names. Empty
     * when no invitation was pending; the returned invitation carries the island and inviter for
     * the caller's messaging.
     */
    public CompletableFuture<Optional<Invitation>> acceptInvite() {
        return playerHandler.acceptInvite(actor, playerUuid);
    }

    public CompletableFuture<Void> rejectInvite() {
        return playerHandler.removeInvite(actor, playerUuid);
    }

    /** MEMBER, enforced in the delete transaction. */
    public CompletableFuture<Void> removeMember(UUID memberUuid) {
        return ownIsland().thenCompose(islandUuid -> playerHandler.removeMember(actor, islandUuid, memberUuid));
    }

    /** OWNER, enforced in the transfer transaction. */
    public CompletableFuture<Void> setOwner(UUID newOwnerUuid) {
        return ownIsland().thenCompose(islandUuid -> playerHandler.setOwner(actor, islandUuid, newOwnerUuid));
    }

    /** MEMBER, enforced in the ban transaction. */
    public CompletableFuture<Void> banPlayer(UUID targetUuid) {
        return ownIsland().thenCompose(islandUuid -> banHandler.addBan(actor, islandUuid, targetUuid));
    }

    /** MEMBER, enforced in the unban transaction. */
    public CompletableFuture<Void> unbanPlayer(UUID targetUuid) {
        return ownIsland().thenCompose(islandUuid -> banHandler.removeBan(actor, islandUuid, targetUuid));
    }

    /** MEMBER, enforced in the coop transaction. */
    public CompletableFuture<Void> addCoop(UUID targetUuid) {
        return ownIsland().thenCompose(islandUuid -> coopHandler.addCoop(actor, islandUuid, targetUuid));
    }

    /** MEMBER, enforced in the uncoop transaction. */
    public CompletableFuture<Void> removeCoop(UUID targetUuid) {
        return ownIsland().thenCompose(islandUuid -> coopHandler.removeCoop(actor, islandUuid, targetUuid));
    }

    /** MEMBER, re-checked on the island's host at the moment of the kick. */
    public CompletableFuture<Void> expelPlayer(UUID targetUuid) {
        return ownIsland().thenCompose(islandUuid -> playerHandler.expelPlayer(actor, islandUuid, targetUuid));
    }

    /** MEMBER, enforced in the toggle transaction. */
    public CompletableFuture<Boolean> toggleLock() {
        return ownIsland().thenCompose(islandUuid -> coreHandler.toggleLock(actor, islandUuid));
    }

    /** MEMBER, enforced in the toggle transaction. */
    public CompletableFuture<Boolean> togglePvp() {
        return ownIsland().thenCompose(islandUuid -> coreHandler.togglePvp(actor, islandUuid));
    }

    // ---- homes and warps --------------------------------------------------------------------

    public CompletableFuture<Void> setHome(String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return homeHandler.setHome(actor, playerUuid, homeName, worldName, x, y, z, yaw, pitch);
    }

    public CompletableFuture<Void> deleteHome(String homeName) {
        return homeHandler.deleteHome(actor, playerUuid, homeName);
    }

    public CompletableFuture<Void> home(String homeName) {
        return homeHandler.teleportToHome(actor, playerUuid, homeName, playerUuid);
    }

    public CompletableFuture<Void> setWarp(String warpName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return warpHandler.setWarp(actor, playerUuid, warpName, worldName, x, y, z, yaw, pitch);
    }

    public CompletableFuture<Void> deleteWarp(String warpName) {
        return warpHandler.deleteWarp(actor, playerUuid, warpName);
    }

    /** Travel to anyone's warp - warps are public to visit; only the traveler is fixed to self. */
    public CompletableFuture<Void> warp(UUID warpOwnerUuid, String warpName) {
        return warpHandler.teleportToWarp(actor, warpOwnerUuid, warpName, playerUuid);
    }

    // ---- world ------------------------------------------------------------------------------

    /** Re-biome a chunk of this player's own island world; any other world is refused. */
    public CompletableFuture<Void> applyBiome(String worldName, int chunkX, int chunkZ, String biomeName) {
        return biomeHandler.applyChunkBiome(actor, worldName, chunkX, chunkZ, biomeName);
    }
}
