package org.me.newsky.api;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.island.*;
import org.me.newsky.message.PlayerMessageHandler;
import org.me.newsky.model.Actor;
import org.me.newsky.model.Invitation;
import org.me.newsky.model.IslandTop;
import org.me.newsky.uuid.UuidHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * The plugin's public surface. Two halves, and the signature tells you which one you are in:
 * <ul>
 *   <li><b>Writes take an {@link Actor} as their first parameter</b> - always, without exception.
 *       Each one's javadoc names the rule it enforces: OWNER or MEMBER (an island role, checked
 *       inside the write transaction under the island row lock), SELF (the actor must be the
 *       player being acted on), or BYPASS (operator, console or internal task only).</li>
 *   <li><b>Reads take no Actor</b> - island state is not secret, and a read changes nothing.</li>
 * </ul>
 * Two operations write without taking an Actor, and both say so where they are declared: they
 * recompute derived data rather than acting on anyone's rights.
 * <p>
 * Pass {@code new Actor.Player(uuid)} from a player command and
 * {@code new Actor.Bypass(sender.getName())} from an admin command or internal task; naming the
 * bypass is what makes skipping the rules deliberate and visible in logs.
 */
public class NewSkyAPI {

    private final NewSky plugin;
    private final CoreHandler coreHandler;
    private final PlayerHandler playerHandler;
    private final HomeHandler homeHandler;
    private final WarpHandler warpHandler;
    private final LevelHandler levelHandler;
    private final BanHandler banHandler;
    private final CoopHandler coopHandler;
    private final LobbyHandler lobbyHandler;
    private final PlayerMessageHandler playerMessageHandler;
    private final BiomeHandler biomeHandler;
    private final UuidHandler uuidHandler;

    public NewSkyAPI(NewSky plugin, CoreHandler coreHandler, PlayerHandler playerHandler, HomeHandler homeHandler, WarpHandler warpHandler, LevelHandler levelHandler, BanHandler banHandler, CoopHandler coopHandler, LobbyHandler lobbyHandler, PlayerMessageHandler playerMessageHandler, UuidHandler uuidHandler, BiomeHandler biomeHandler) {
        this.plugin = plugin;
        this.coreHandler = coreHandler;
        this.playerHandler = playerHandler;
        this.homeHandler = homeHandler;
        this.warpHandler = warpHandler;
        this.levelHandler = levelHandler;
        this.banHandler = banHandler;
        this.coopHandler = coopHandler;
        this.lobbyHandler = lobbyHandler;
        this.playerMessageHandler = playerMessageHandler;
        this.uuidHandler = uuidHandler;
        this.biomeHandler = biomeHandler;
    }

    // ================================================================================================================
    // Writes - Actor first, every time. The javadoc names the rule.
    // ================================================================================================================

    @SuppressWarnings("unused")
    public CompletableFuture<Void> createIsland(Actor actor, UUID ownerPlayerUuid) {
        return coreHandler.createIsland(actor, ownerPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> deleteIsland(Actor actor, UUID islandUuid) {
        return coreHandler.deleteIsland(actor, islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> loadIsland(Actor actor, UUID islandUuid) {
        return coreHandler.loadIsland(actor, islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> unloadIsland(Actor actor, UUID islandUuid) {
        return coreHandler.unloadIsland(actor, islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> addMember(Actor actor, UUID islandUuid, UUID playerUuid, String role) {
        return playerHandler.addMember(actor, islandUuid, playerUuid, role);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeMember(Actor actor, UUID islandUuid, UUID playerUuid) {
        return playerHandler.removeMember(actor, islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> setOwner(Actor actor, UUID islandUuid, UUID newOwnerPlayerUuid) {
        return playerHandler.setOwner(actor, islandUuid, newOwnerPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> addPendingInvite(Actor actor, UUID islandUuid, UUID inviteeUuid, int ttlSeconds) {
        return playerHandler.addPendingInvite(actor, islandUuid, inviteeUuid, ttlSeconds);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removePendingInvite(Actor actor, UUID inviteeUuid) {
        return playerHandler.removePendingInvite(actor, inviteeUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Optional<Invitation>> consumePendingInvite(Actor actor, UUID inviteeUuid) {
        return playerHandler.consumePendingInvite(actor, inviteeUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> expelPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return playerHandler.expelPlayer(actor, islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> banPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return banHandler.banPlayer(actor, islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> unbanPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        return banHandler.unbanPlayer(actor, islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> addCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return coopHandler.coopPlayer(actor, islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        return coopHandler.unCoopPlayer(actor, islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeAllCoopOfPlayer(Actor actor, UUID playerUuid) {
        return coopHandler.deleteAllCoopOfPlayer(actor, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> toggleIslandLock(Actor actor, UUID islandUuid) {
        return coreHandler.toggleIslandLock(actor, islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> toggleIslandPvp(Actor actor, UUID islandUuid) {
        return coreHandler.toggleIslandPvp(actor, islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> setHome(Actor actor, UUID playerUuid, String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return homeHandler.setHome(actor, playerUuid, homeName, worldName, x, y, z, yaw, pitch);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> delHome(Actor actor, UUID playerUuid, String homeName) {
        return homeHandler.delHome(actor, playerUuid, homeName);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> home(Actor actor, UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return homeHandler.home(actor, playerUuid, homeName, targetPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> setWarp(Actor actor, UUID playerUuid, String warpName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return warpHandler.setWarp(actor, playerUuid, warpName, worldName, x, y, z, yaw, pitch);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> delWarp(Actor actor, UUID playerUuid, String warpName) {
        return warpHandler.delWarp(actor, playerUuid, warpName);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> warp(Actor actor, UUID warpPlayerUuid, String warpName, UUID targetPlayerUuid) {
        return warpHandler.warp(actor, warpPlayerUuid, warpName, targetPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> applyChunkBiome(Actor actor, String worldName, int chunkX, int chunkZ, String biomeName) {
        return biomeHandler.applyChunkBiome(actor, worldName, chunkX, chunkZ, biomeName);
    }

    // ================================================================================================================
    // Reads - no Actor, ever
    // ================================================================================================================

    @SuppressWarnings("unused")
    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return coreHandler.getIslandUuid(playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return playerHandler.getIslandOwner(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return playerHandler.getIslandMembers(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<UUID>> getIslandPlayers(UUID islandUuid) {
        return playerHandler.getIslandPlayers(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Optional<Invitation>> getPendingInvite(UUID playerUuid) {
        return playerHandler.getPendingInvite(playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> isIslandLock(UUID islandUuid) {
        return coreHandler.isIslandLock(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> isIslandPvp(UUID islandUuid) {
        return coreHandler.isIslandPvp(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<String>> getHomeNames(UUID playerUuid) {
        return homeHandler.getHomeNames(playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<String>> getWarpNames(UUID playerUuid) {
        return warpHandler.getWarpNames(playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        return banHandler.isPlayerBanned(islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<UUID>> getBannedPlayers(UUID islandUuid) {
        return banHandler.getBannedPlayers(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return coopHandler.isPlayerCooped(islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<UUID>> getCoopedPlayers(UUID islandUuid) {
        return coopHandler.getCoopedPlayers(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Integer> getIslandLevel(UUID islandUuid) {
        return levelHandler.getIslandLevel(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<List<IslandTop>> getTopIslandLevels(int limit) {
        return levelHandler.getTopIslandLevels(limit);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Optional<UUID>> getPlayerUuid(String name) {
        return uuidHandler.getPlayerUuid(name);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Optional<String>> getPlayerName(UUID uuid) {
        return uuidHandler.getPlayerName(uuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Map<UUID, String>> getPlayerNames(Collection<UUID> uuids) {
        return uuidHandler.getPlayerNames(uuids);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<UUID>> getOnlinePlayersUUIDs() {
        return plugin.getOnlinePlayersUUIDs();
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<String>> getOnlinePlayersNames() {
        return plugin.getOnlinePlayersNames();
    }

    // ================================================================================================================
    // Live-player actions and derived data - no island state, so no Actor
    // ================================================================================================================

    @SuppressWarnings("unused")
    public CompletableFuture<Void> lobby(UUID playerUuid) {
        return lobbyHandler.lobby(playerUuid);
    }

    @SuppressWarnings("unused")
    public void sendPlayerMessage(UUID playerUuid, Component message) {
        playerMessageHandler.sendPlayerMessage(playerUuid, message);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Integer> calIslandLevel(UUID islandUuid) {
        return levelHandler.calIslandLevel(islandUuid);
    }

    @SuppressWarnings("all")
    public CompletableFuture<Void> updatePlayerUuid(UUID uuid, String name) {
        return uuidHandler.updatePlayerUuid(uuid, name);
    }
}
