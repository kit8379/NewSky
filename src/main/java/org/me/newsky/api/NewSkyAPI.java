package org.me.newsky.api;

import org.bukkit.Location;
import org.me.newsky.island.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NewSkyAPI {

    private final IslandHandler islandHandler;
    private final PlayerHandler playerHandler;
    private final HomeHandler homeHandler;
    private final WarpHandler warpHandler;
    private final LevelHandler levelHandler;

    public NewSkyAPI(IslandHandler islandHandler, PlayerHandler playerHandler, HomeHandler homeHandler, WarpHandler warpHandler, LevelHandler levelHandler) {
        this.islandHandler = islandHandler;
        this.playerHandler = playerHandler;
        this.homeHandler = homeHandler;
        this.warpHandler = warpHandler;
        this.levelHandler = levelHandler;

    }

    // Island Operation API methods

    /**
     * Create an island for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes when the island is created
     */
    public CompletableFuture<Void> createIsland(UUID playerUuid) {
        return islandHandler.createIsland(playerUuid);
    }


    /**
     * Delete an island for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes when the island is deleted
     */
    public CompletableFuture<Void> deleteIsland(UUID playerUuid) {
        return islandHandler.deleteIsland(playerUuid);
    }


    /**
     * Load an island for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes when the island is loaded
     */
    public CompletableFuture<Void> loadIsland(UUID playerUuid) {
        return islandHandler.loadIsland(playerUuid);
    }


    /**
     * Unload an island for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes when the island is unloaded
     */
    public CompletableFuture<Void> unloadIsland(UUID playerUuid) {
        return islandHandler.unloadIsland(playerUuid);
    }


    /**
     * Toggle the lock status of the island for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the new lock status
     */
    public CompletableFuture<Boolean> toggleIslandLock(UUID playerUuid) {
        return islandHandler.toggleIslandLock(playerUuid);
    }


    /**
     * Toggle the PvP status of the island for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the new PvP status
     */
    public CompletableFuture<Boolean> toggleIslandPvp(UUID playerUuid) {
        return islandHandler.toggleIslandPvp(playerUuid);
    }

    // Island Player API methods

    /**
     * Get the owner of the island for the specified player
     *
     * @param playerUuid The UUID of the player
     * @return A CompletableFuture that completes with the UUID of the island owner
     */
    public CompletableFuture<Void> addMember(UUID islandOwnerId, UUID playerUuid, String role) {
        return playerHandler.addMember(islandOwnerId, playerUuid, role);
    }


    /**
     * Add a member to the island for the specified player
     *
     * @param islandOwnerId The UUID of the island owner
     * @param playerUuid    The UUID of the player to add
     * @return A CompletableFuture that completes when the player is added
     */
    public CompletableFuture<Void> removeMember(UUID islandOwnerId, UUID playerUuid) {
        return playerHandler.removeMember(islandOwnerId, playerUuid);
    }


    /**
     * Set the owner of the island for the specified player
     *
     * @param islandOwnerId The UUID of the current island owner
     * @param newOwnerId    The UUID of the new island owner
     * @return A CompletableFuture that completes when the owner is set
     */
    public CompletableFuture<Void> setOwner(UUID islandOwnerId, UUID newOwnerId) {
        return playerHandler.setOwner(islandOwnerId, newOwnerId);
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
     * @param playerUuid The UUID of the player
     * @param homeName   The name of the home
     * @return A CompletableFuture that completes when the player is teleported
     */
    public CompletableFuture<Void> home(UUID playerUuid, String homeName) {
        return homeHandler.home(playerUuid, homeName);
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
     * @param playerUuid The UUID of the player
     * @param warpName   The name of the warp
     * @return A CompletableFuture that completes when the player is teleported
     */
    public CompletableFuture<Void> warp(UUID playerUuid, String warpName) {
        return warpHandler.warp(playerUuid, warpName);
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
     * Calculate the level of the island
     *
     * @param playerUuid The UUID of the island
     * @return A CompletableFuture that completes when the level is calculated
     */
    public CompletableFuture<Void> calIslandLevel(UUID playerUuid) {
        return levelHandler.calIslandLevel(playerUuid);
    }


    /**
     * Get the level of the island
     *
     * @param playerUuid The UUID of the island
     * @return A CompletableFuture that completes with the level of the island
     */
    public CompletableFuture<Integer> getIslandLevel(UUID playerUuid) {
        return levelHandler.getIslandLevel(playerUuid);
    }
}