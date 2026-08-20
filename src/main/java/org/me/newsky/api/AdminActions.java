package org.me.newsky.api;

import org.me.newsky.island.BanHandler;
import org.me.newsky.island.BiomeHandler;
import org.me.newsky.island.CoopHandler;
import org.me.newsky.island.CoreHandler;
import org.me.newsky.island.HomeHandler;
import org.me.newsky.island.PlayerHandler;
import org.me.newsky.island.WarpHandler;
import org.me.newsky.model.Actor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Operator-level actions on arbitrary islands and players, exempt from every player-facing rule.
 * Obtained via {@code api.admin(sender)} - the sender's name travels with each write into logs
 * and cross-server payloads, which is the only accountability a bypass has.
 */
public final class AdminActions {

    private final Actor actor;
    private final CoreHandler coreHandler;
    private final PlayerHandler playerHandler;
    private final HomeHandler homeHandler;
    private final WarpHandler warpHandler;
    private final BanHandler banHandler;
    private final CoopHandler coopHandler;
    private final BiomeHandler biomeHandler;

    AdminActions(String source, CoreHandler coreHandler, PlayerHandler playerHandler, HomeHandler homeHandler, WarpHandler warpHandler, BanHandler banHandler, CoopHandler coopHandler, BiomeHandler biomeHandler) {
        this.actor = new Actor.Bypass(source);
        this.coreHandler = coreHandler;
        this.playerHandler = playerHandler;
        this.homeHandler = homeHandler;
        this.warpHandler = warpHandler;
        this.banHandler = banHandler;
        this.coopHandler = coopHandler;
        this.biomeHandler = biomeHandler;
    }

    // ---- island lifecycle -------------------------------------------------------------------

    public CompletableFuture<Void> createIsland(UUID ownerPlayerUuid) {
        return coreHandler.createIsland(actor, ownerPlayerUuid);
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return coreHandler.deleteIsland(actor, islandUuid);
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return coreHandler.loadIsland(actor, islandUuid);
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return coreHandler.unloadIsland(actor, islandUuid);
    }

    // ---- membership and trust ---------------------------------------------------------------

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return playerHandler.addMember(actor, islandUuid, playerUuid, role);
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return playerHandler.removeMember(actor, islandUuid, playerUuid);
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerUuid) {
        return playerHandler.setOwner(actor, islandUuid, newOwnerUuid);
    }

    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID targetUuid) {
        return banHandler.addBan(actor, islandUuid, targetUuid);
    }

    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID targetUuid) {
        return banHandler.removeBan(actor, islandUuid, targetUuid);
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, UUID targetUuid) {
        return coopHandler.addCoop(actor, islandUuid, targetUuid);
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, UUID targetUuid) {
        return coopHandler.removeCoop(actor, islandUuid, targetUuid);
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID targetUuid) {
        return playerHandler.expelPlayer(actor, islandUuid, targetUuid);
    }

    public CompletableFuture<Boolean> toggleLock(UUID islandUuid) {
        return coreHandler.toggleLock(actor, islandUuid);
    }

    public CompletableFuture<Boolean> togglePvp(UUID islandUuid) {
        return coreHandler.togglePvp(actor, islandUuid);
    }

    // ---- homes and warps (on anyone's behalf) -------------------------------------------------

    public CompletableFuture<Void> setHome(UUID homeOwnerUuid, String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return homeHandler.setHome(actor, homeOwnerUuid, homeName, worldName, x, y, z, yaw, pitch);
    }

    public CompletableFuture<Void> deleteHome(UUID homeOwnerUuid, String homeName) {
        return homeHandler.deleteHome(actor, homeOwnerUuid, homeName);
    }

    public CompletableFuture<Void> home(UUID homeOwnerUuid, String homeName, UUID teleportPlayerUuid) {
        return homeHandler.teleportToHome(actor, homeOwnerUuid, homeName, teleportPlayerUuid);
    }

    public CompletableFuture<Void> setWarp(UUID warpOwnerUuid, String warpName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return warpHandler.setWarp(actor, warpOwnerUuid, warpName, worldName, x, y, z, yaw, pitch);
    }

    public CompletableFuture<Void> deleteWarp(UUID warpOwnerUuid, String warpName) {
        return warpHandler.deleteWarp(actor, warpOwnerUuid, warpName);
    }

    public CompletableFuture<Void> warp(UUID warpOwnerUuid, String warpName, UUID teleportPlayerUuid) {
        return warpHandler.teleportToWarp(actor, warpOwnerUuid, warpName, teleportPlayerUuid);
    }

    // ---- world ------------------------------------------------------------------------------

    public CompletableFuture<Void> applyBiome(String worldName, int chunkX, int chunkZ, String biomeName) {
        return biomeHandler.applyChunkBiome(actor, worldName, chunkX, chunkZ, biomeName);
    }
}
