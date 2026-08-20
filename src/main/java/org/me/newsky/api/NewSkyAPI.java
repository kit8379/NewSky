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

    @SuppressWarnings("unused")
    public CompletableFuture<Void> createIsland(UUID ownerPlayerUuid) {
        return coreHandler.createIsland(ownerPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> deleteIsland(UUID islandUuid, Actor actor) {
        return coreHandler.deleteIsland(islandUuid, actor);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return coreHandler.loadIsland(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return coreHandler.unloadIsland(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return playerHandler.addMember(islandUuid, playerUuid, role);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeMember(UUID islandUuid, Actor actor, UUID playerUuid) {
        return playerHandler.removeMember(islandUuid, actor, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> addPendingInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        return playerHandler.addPendingInvite(inviteeUuid, islandUuid, inviterUuid, ttlSeconds);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removePendingInvite(UUID playerUuid) {
        return playerHandler.removePendingInvite(playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Optional<Invitation>> getPendingInvite(UUID playerUuid) {
        return playerHandler.getPendingInvite(playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> setOwner(UUID islandUuid, Actor actor, UUID newOwnerPlayerUuid) {
        return playerHandler.setOwner(islandUuid, actor, newOwnerPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return homeHandler.setHome(playerUuid, homeName, worldName, x, y, z, yaw, pitch);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return homeHandler.delHome(playerUuid, homeName);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return homeHandler.home(playerUuid, homeName, targetPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return warpHandler.setWarp(playerUuid, warpName, worldName, x, y, z, yaw, pitch);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return warpHandler.delWarp(playerUuid, warpName);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> warp(UUID warpPlayerUuid, String warpName, UUID targetPlayerUuid) {
        return warpHandler.warp(warpPlayerUuid, warpName, targetPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> expelPlayer(UUID islandUuid, Actor actor, UUID playerUuid) {
        return playerHandler.expelPlayer(islandUuid, actor, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> banPlayer(UUID islandUuid, Actor actor, UUID playerUuid) {
        return banHandler.banPlayer(islandUuid, actor, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, Actor actor, UUID playerUuid) {
        return banHandler.unbanPlayer(islandUuid, actor, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> addCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return coopHandler.coopPlayer(islandUuid, actor, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        return coopHandler.unCoopPlayer(islandUuid, actor, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeAllCoopOfPlayer(UUID playerUuid) {
        return coopHandler.deleteAllCoopOfPlayer(playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid, Actor actor) {
        return coreHandler.toggleIslandLock(islandUuid, actor);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid, Actor actor) {
        return coreHandler.toggleIslandPvp(islandUuid, actor);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Integer> calIslandLevel(UUID islandUuid) {
        return levelHandler.calIslandLevel(islandUuid);
    }

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
    public CompletableFuture<Void> applyChunkBiome(String worldName, int chunkX, int chunkZ, String biomeName) {
        return biomeHandler.applyChunkBiome(worldName, chunkX, chunkZ, biomeName);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> applyPlayerChunkBiome(UUID playerUuid, String worldName, int chunkX, int chunkZ, String biomeName) {
        return biomeHandler.applyPlayerChunkBiome(playerUuid, worldName, chunkX, chunkZ, biomeName);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> lobby(UUID playerUuid) {
        return lobbyHandler.lobby(playerUuid);
    }

    @SuppressWarnings("unused")
    public void sendPlayerMessage(UUID playerUuid, Component message) {
        playerMessageHandler.sendPlayerMessage(playerUuid, message);
    }

    @SuppressWarnings("all")
    public CompletableFuture<Void> updatePlayerUuid(UUID uuid, String name) {
        return uuidHandler.updatePlayerUuid(uuid, name);
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
}