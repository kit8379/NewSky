package org.me.newsky.api;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.island.*;
import org.me.newsky.message.PlayerMessageHandler;
import org.me.newsky.model.Invitation;
import org.me.newsky.model.UpgradeResult;
import org.me.newsky.uuid.UuidHandler;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NewSkyAPI {

    private final NewSky plugin;
    private final IslandHandler islandHandler;
    private final PlayerHandler playerHandler;
    private final HomeHandler homeHandler;
    private final WarpHandler warpHandler;
    private final LevelHandler levelHandler;
    private final BanHandler banHandler;
    private final CoopHandler coopHandler;
    private final LobbyHandler lobbyHandler;
    private final PlayerMessageHandler playerMessageHandler;
    private final UpgradeHandler upgradeHandler;
    private final UuidHandler uuidHandler;

    public NewSkyAPI(NewSky plugin, IslandHandler islandHandler, PlayerHandler playerHandler, HomeHandler homeHandler, WarpHandler warpHandler, LevelHandler levelHandler, BanHandler banHandler, CoopHandler coopHandler, LobbyHandler lobbyHandler, PlayerMessageHandler playerMessageHandler, UuidHandler uuidHandler, UpgradeHandler upgradeHandler) {

        this.plugin = plugin;
        this.islandHandler = islandHandler;
        this.playerHandler = playerHandler;
        this.homeHandler = homeHandler;
        this.warpHandler = warpHandler;
        this.levelHandler = levelHandler;
        this.banHandler = banHandler;
        this.coopHandler = coopHandler;
        this.lobbyHandler = lobbyHandler;
        this.playerMessageHandler = playerMessageHandler;
        this.upgradeHandler = upgradeHandler;
        this.uuidHandler = uuidHandler;
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> createIsland(UUID ownerPlayerUuid) {
        return islandHandler.createIsland(ownerPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return islandHandler.deleteIsland(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return islandHandler.loadIsland(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return islandHandler.unloadIsland(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return playerHandler.addMember(islandUuid, playerUuid, role);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return playerHandler.removeMember(islandUuid, playerUuid);
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
    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerPlayerUuid) {
        return playerHandler.setOwner(islandUuid, newOwnerPlayerUuid);
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
    public CompletableFuture<Void> warp(UUID playerUuid, String warpName, UUID targetPlayerUuid) {
        return warpHandler.warp(playerUuid, warpName, targetPlayerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return playerHandler.expelPlayer(islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID playerUuid) {
        return banHandler.banPlayer(islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID playerUuid) {
        return banHandler.unbanPlayer(islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> addCoop(UUID islandUuid, UUID playerUuid) {
        return coopHandler.coopPlayer(islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeCoop(UUID islandUuid, UUID playerUuid) {
        return coopHandler.unCoopPlayer(islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeAllCoopOfPlayer(UUID playerUuid) {
        return coopHandler.deleteAllCoopOfPlayer(playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return islandHandler.toggleIslandLock(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return islandHandler.toggleIslandPvp(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Integer> calIslandLevel(UUID islandUuid) {
        return levelHandler.calIslandLevel(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return islandHandler.getIslandUuid(playerUuid);
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
        return islandHandler.isIslandLock(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> isIslandPvp(UUID islandUuid) {
        return islandHandler.isIslandPvp(islandUuid);
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
    public CompletableFuture<UpgradeResult> upgradeToNextLevel(UUID islandUuid, String upgradeId) {
        return upgradeHandler.upgradeToNextLevel(islandUuid, upgradeId);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Void> setUpgradeLevel(UUID islandUuid, String upgradeId, int level) {
        return upgradeHandler.setUpgradeLevel(islandUuid, upgradeId, level);
    }

    @SuppressWarnings("unused")
    public Set<String> getUpgradeIds() {
        return upgradeHandler.getUpgradeIds();
    }

    @SuppressWarnings("unused")
    public Set<Integer> getUpgradeLevels(String upgradeId) {
        return upgradeHandler.getUpgradeLevels(upgradeId);
    }

    @SuppressWarnings("unused")
    public int getNextUpgradeLevel(String upgradeId, int currentLevel) {
        return upgradeHandler.getNextUpgradeLevel(upgradeId, currentLevel);
    }

    @SuppressWarnings("unused")
    public int getUpgradeRequireIslandLevel(String upgradeId, int level) {
        return upgradeHandler.getUpgradeRequireIslandLevel(upgradeId, level);
    }

    @SuppressWarnings("unused")
    public int getTeamLimit(int level) {
        return upgradeHandler.getTeamLimit(level);
    }

    @SuppressWarnings("unused")
    public int getWarpLimit(int level) {
        return upgradeHandler.getWarpLimit(level);
    }

    @SuppressWarnings("unused")
    public int getHomeLimit(int level) {
        return upgradeHandler.getHomeLimit(level);
    }

    @SuppressWarnings("unused")
    public int getCoopLimit(int level) {
        return upgradeHandler.getCoopLimit(level);
    }

    @SuppressWarnings("unused")
    public int getIslandSize(int level) {
        return upgradeHandler.getIslandSize(level);
    }

    @SuppressWarnings("unused")
    public Map<String, Double> getGeneratorRates(int level) {
        return upgradeHandler.getGeneratorRates(level);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Integer> getCurrentUpgradeLevel(UUID islandUuid, String upgradeId) {
        return upgradeHandler.getCurrentUpgradeLevel(islandUuid, upgradeId);
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
    public CompletableFuture<Set<UUID>> getOnlinePlayersUUIDs() {
        return plugin.getOnlinePlayersUUIDs();
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<String>> getOnlinePlayersNames() {
        return plugin.getOnlinePlayersNames();
    }
}