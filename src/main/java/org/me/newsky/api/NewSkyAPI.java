package org.me.newsky.api;

import org.bukkit.Location;
import org.me.newsky.NewSky;
import org.me.newsky.island.*;

import java.util.List;
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

    public NewSkyAPI(NewSky plugin, IslandHandler islandHandler, PlayerHandler playerHandler, HomeHandler homeHandler, WarpHandler warpHandler, LevelHandler levelHandler, BanHandler banHandler) {
        this.plugin = plugin;
        this.islandHandler = islandHandler;
        this.playerHandler = playerHandler;
        this.homeHandler = homeHandler;
        this.warpHandler = warpHandler;
        this.levelHandler = levelHandler;
        this.banHandler = banHandler;
    }

    // Island Operation API methods

    /**
     * Get the UUID of the island for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the UUID of the island
     */
    public CompletableFuture<UUID> getIslandUuid(UUID playerUuid) {
        return islandHandler.getIslandUuid(playerUuid);
    }

    /**
     * Get the owner of the island
     *
     * @param islandUuid The UUID of the player
     * @return A CompletableFuture that completes with the UUID of the island owner
     */
    public CompletableFuture<UUID> getIslandOwner(UUID islandUuid) {
        return islandHandler.getIslandOwner(islandUuid);
    }

    /**
     * Get the members of the island
     *
     * @param islandUuid The UUID of the player
     * @return A CompletableFuture that completes with the set of island members
     */
    public CompletableFuture<Set<UUID>> getIslandMembers(UUID islandUuid) {
        return islandHandler.getIslandMembers(islandUuid);
    }


    /**
     * Create an island for the specified player
     *
     * @param ownerPlayerUuid The UUID of the island owner
     * @return A CompletableFuture that completes when the island is created
     */
    public CompletableFuture<Void> createIsland(UUID ownerPlayerUuid) {
        return islandHandler.createIsland(ownerPlayerUuid);
    }


    /**
     * Delete an island for the specified player
     *
     * @param islandUuid The UUID of the island
     * @return A CompletableFuture that completes when the island is deleted
     */
    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return islandHandler.deleteIsland(islandUuid);
    }


    /**
     * Load an island for the specified player
     *
     * @param islandUuid The UUID of the island
     * @return A CompletableFuture that completes when the island is loaded
     */
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return islandHandler.loadIsland(islandUuid);
    }


    /**
     * Unload an island for the specified player
     *
     * @param islandUuid The UUID of the island
     * @return A CompletableFuture that completes when the island is unloaded
     */
    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return islandHandler.unloadIsland(islandUuid);
    }


    /**
     * Toggle the lock status of the island for the specified player
     *
     * @param islandUuid The UUID of the island
     * @return A CompletableFuture that completes with the new lock status
     */
    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return islandHandler.toggleIslandLock(islandUuid);
    }


    /**
     * Toggle the PvP status of the island for the specified player
     *
     * @param islandUuid The UUID of the island
     * @return A CompletableFuture that completes with the new PvP status
     */
    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return islandHandler.toggleIslandPvp(islandUuid);
    }

    // Island Player API methods

    /**
     * Get the owner of the island for the specified player
     *
     * @param islandUuid The UUID of the island
     * @param playerUuid The UUID of the player to add
     * @return A CompletableFuture that completes with the UUID of the island owner
     */
    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return playerHandler.addMember(islandUuid, playerUuid, role);
    }


    /**
     * Remove a member to the island for the specified player
     *
     * @param islandUuid The UUID of the island
     * @param playerUuid The UUID of the player to remove
     * @return A CompletableFuture that completes when the player is added
     */
    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return playerHandler.removeMember(islandUuid, playerUuid);
    }


    /**
     * Set the owner of the island for the specified player
     *
     * @param islandUuid         The UUID of the island
     * @param newOwnerPlayerUuid The UUID of the new island owner
     * @return A CompletableFuture that completes when the owner is set
     */
    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerPlayerUuid) {
        return playerHandler.setOwner(islandUuid, newOwnerPlayerUuid);
    }


    // Home API methods

    /**
     * Set a home for the specified player
     *
     * @param playerUuid The UUID of the player
     * @param homeName   The name of the home
     * @param location   The location of the home
     * @return A CompletableFuture that completes when the home is set
     */
    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, Location location) {
        return homeHandler.setHome(playerUuid, homeName, location);
    }


    /**
     * Delete a home for the specified player
     *
     * @param playerUuid The UUID of the player
     * @param homeName   The name of the home
     * @return A CompletableFuture that completes when the home is deleted
     */
    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return homeHandler.delHome(playerUuid, homeName);
    }


    /**
     * Teleport to a home for the specified player
     *
     * @param playerUuid       The UUID of the player
     * @param homeName         The name of the home
     * @param targetPlayerUuid The UUID of the player to teleport
     * @return A CompletableFuture that completes when the player is teleported
     */
    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return homeHandler.home(playerUuid, homeName, targetPlayerUuid);
    }


    /**
     * Get the names of the homes for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the set of home names
     */
    public CompletableFuture<Set<String>> getHomeNames(UUID playerUuid) {
        return homeHandler.getHomeNames(playerUuid);
    }


    // Warp API methods

    /**
     * Set a warp for the specified player
     *
     * @param playerUuid The UUID of the player
     * @param warpName   The name of the warp
     * @param location   The location of the warp
     * @return A CompletableFuture that completes when the warp is set
     */
    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, Location location) {
        return warpHandler.setWarp(playerUuid, warpName, location);
    }


    /**
     * Delete a warp for the specified player
     *
     * @param playerUuid The UUID of the player
     * @param warpName   The name of the warp
     * @return A CompletableFuture that completes when the warp is deleted
     */
    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return warpHandler.delWarp(playerUuid, warpName);
    }


    /**
     * Teleport to a warp for the specified player
     *
     * @param playerUuid       The UUID of the player
     * @param warpName         The name of the warp
     * @param targetPlayerUuid The UUID of the player to teleport
     * @return A CompletableFuture that completes when the player is teleported
     */
    public CompletableFuture<Void> warp(UUID playerUuid, String warpName, UUID targetPlayerUuid) {
        return warpHandler.warp(playerUuid, warpName, targetPlayerUuid);
    }


    /**
     * Get the names of the warps for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the set of warp names
     */
    public CompletableFuture<Set<String>> getWarpNames(UUID playerUuid) {
        return warpHandler.getWarpNames(playerUuid);
    }


    /**
     * Get the level of the island
     *
     * @param islandUuid The UUID of the island
     * @return A CompletableFuture that completes with the level of the island
     */
    public CompletableFuture<Integer> getIslandLevel(UUID islandUuid) {
        return levelHandler.getIslandLevel(islandUuid);
    }

    /**
     * Get the top island levels
     *
     * @param size The size of the top list
     * @return A CompletableFuture that completes with the top island levels
     */
    public CompletableFuture<List<Map.Entry<UUID, Integer>>> getTopIslandLevels(int size) {
        return levelHandler.getTopIslandLevels(size);
    }

    /**
     * Expel a player from the island
     *
     * @param islandUuid The UUID of the island
     * @param playerUuid The UUID of the player to expel
     * @return A CompletableFuture that completes when the player is expelled
     */
    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return islandHandler.expelPlayer(islandUuid, playerUuid);
    }

    /**
     * Ban a player from the island
     *
     * @param islandUuid The UUID of the island
     * @param playerUuid The UUID of the player to ban
     * @return A CompletableFuture that completes when the player is banned
     */
    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID playerUuid) {
        return banHandler.banPlayer(islandUuid, playerUuid);
    }

    /**
     * Unban a player from the island
     *
     * @param islandUuid The UUID of the island
     * @param playerUuid The UUID of the player to unban
     * @return A CompletableFuture that completes when the player is unbanned
     */
    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID playerUuid) {
        return banHandler.unbanPlayer(islandUuid, playerUuid);
    }

    /**
     * Get the banned players of the island
     *
     * @param islandUuid The UUID of the island
     * @return A CompletableFuture that completes with the set of banned players
     */
    public CompletableFuture<Set<UUID>> getBannedPlayers(UUID islandUuid) {
        return banHandler.getBannedPlayers(islandUuid);
    }

    /**
     * Get the online players
     *
     * @return A set of online players
     */
    public Set<String> getOnlinePlayers() {
        return plugin.getOnlinePlayers();
    }
}