package org.me.newsky.api;

import org.bukkit.Location;
import org.me.newsky.NewSky;
import org.me.newsky.island.*;

import java.util.Map;
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

    public NewSkyAPI(NewSky plugin, IslandHandler islandHandler, PlayerHandler playerHandler, HomeHandler homeHandler, WarpHandler warpHandler, LevelHandler levelHandler, BanHandler banHandler, CoopHandler coopHandler) {
        this.plugin = plugin;
        this.islandHandler = islandHandler;
        this.playerHandler = playerHandler;
        this.homeHandler = homeHandler;
        this.warpHandler = warpHandler;
        this.levelHandler = levelHandler;
        this.banHandler = banHandler;
        this.coopHandler = coopHandler;
    }

    // Async Operations
    public CompletableFuture<Void> createIsland(UUID ownerPlayerUuid) {
        return islandHandler.createIsland(ownerPlayerUuid);
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return islandHandler.deleteIsland(islandUuid);
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return islandHandler.loadIsland(islandUuid);
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return islandHandler.unloadIsland(islandUuid);
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return playerHandler.addMember(islandUuid, playerUuid, role);
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return playerHandler.removeMember(islandUuid, playerUuid);
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerPlayerUuid) {
        return playerHandler.setOwner(islandUuid, newOwnerPlayerUuid);
    }

    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, Location location) {
        return homeHandler.setHome(playerUuid, homeName, location);
    }

    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return homeHandler.delHome(playerUuid, homeName);
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return homeHandler.home(playerUuid, homeName, targetPlayerUuid);
    }

    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, Location location) {
        return warpHandler.setWarp(playerUuid, warpName, location);
    }

    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return warpHandler.delWarp(playerUuid, warpName);
    }

    public CompletableFuture<Void> warp(UUID playerUuid, String warpName, UUID targetPlayerUuid) {
        return warpHandler.warp(playerUuid, warpName, targetPlayerUuid);
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return islandHandler.expelPlayer(islandUuid, playerUuid);
    }

    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID playerUuid) {
        return banHandler.banPlayer(islandUuid, playerUuid);
    }

    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID playerUuid) {
        return banHandler.unbanPlayer(islandUuid, playerUuid);
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, UUID playerUuid) {
        return coopHandler.addCoop(islandUuid, playerUuid);
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, UUID playerUuid) {
        return coopHandler.removeCoop(islandUuid, playerUuid);
    }

    public void removeAllCoopOfPlayer(UUID playerUuid) {
        coopHandler.removeAllCoopOfPlayer(playerUuid);
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return islandHandler.toggleIslandLock(islandUuid);
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return islandHandler.toggleIslandPvp(islandUuid);
    }

    // Getters
    public UUID getIslandUuid(UUID playerUuid) {
        return islandHandler.getIslandUuid(playerUuid);
    }

    public UUID getIslandOwner(UUID islandUuid) {
        return islandHandler.getIslandOwner(islandUuid);
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        return islandHandler.getIslandMembers(islandUuid);
    }

    public Set<String> getHomeNames(UUID playerUuid) {
        return homeHandler.getHomeNames(playerUuid);
    }

    public Set<String> getWarpNames(UUID playerUuid) {
        return warpHandler.getWarpNames(playerUuid);
    }

    public int getIslandLevel(UUID islandUuid) {
        return levelHandler.getIslandLevel(islandUuid);
    }

    public Map<UUID, Integer> getTopIslandLevels(int size) {
        return levelHandler.getTopIslandLevels(size);
    }

    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        return banHandler.getBannedPlayers(islandUuid);
    }

    public Set<UUID> getCoopedPlayers(UUID islandUuid) {
        return coopHandler.getCoopedPlayers(islandUuid);
    }

    public Set<String> getOnlinePlayers() {
        return plugin.getOnlinePlayers();
    }
}