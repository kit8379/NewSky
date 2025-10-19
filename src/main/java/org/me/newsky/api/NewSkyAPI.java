package org.me.newsky.api;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.me.newsky.NewSky;
import org.me.newsky.island.*;
import org.me.newsky.model.Invitation;
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
    private final LimitHandler limitHandler;
    private final BanHandler banHandler;
    private final CoopHandler coopHandler;
    private final LobbyHandler lobbyHandler;
    private final MessageHandler messageHandler;
    private final UuidHandler uuidHandler;

    public NewSkyAPI(NewSky plugin, IslandHandler islandHandler, PlayerHandler playerHandler, HomeHandler homeHandler, WarpHandler warpHandler, LevelHandler levelHandler, LimitHandler limitHandler, BanHandler banHandler, CoopHandler coopHandler, LobbyHandler lobbyHandler, MessageHandler messageHandler, UuidHandler uuidHandler) {
        this.plugin = plugin;
        this.islandHandler = islandHandler;
        this.playerHandler = playerHandler;
        this.homeHandler = homeHandler;
        this.warpHandler = warpHandler;
        this.levelHandler = levelHandler;
        this.limitHandler = limitHandler;
        this.banHandler = banHandler;
        this.coopHandler = coopHandler;
        this.lobbyHandler = lobbyHandler;
        this.messageHandler = messageHandler;
        this.uuidHandler = uuidHandler;
    }


    /**
     * Creates a new island for the specified owner player.
     *
     * @param ownerPlayerUuid The UUID of the player who will own the island.
     * @return A CompletableFuture that completes when the island is created.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> createIsland(UUID ownerPlayerUuid) {
        return islandHandler.createIsland(ownerPlayerUuid);
    }

    /**
     * Deletes the island with the specified UUID.
     *
     * @param islandUuid The UUID of the island to delete.
     * @return A CompletableFuture that completes when the island is deleted.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return islandHandler.deleteIsland(islandUuid);
    }

    /**
     * Loads the island with the specified UUID.
     *
     * @param islandUuid The UUID of the island to load.
     * @return A CompletableFuture that completes when the island is loaded.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return islandHandler.loadIsland(islandUuid);
    }

    /**
     * Unloads the island with the specified UUID.
     *
     * @param islandUuid The UUID of the island to unload.
     * @return A CompletableFuture that completes when the island is unloaded.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return islandHandler.unloadIsland(islandUuid);
    }

    /**
     * Adds a member to the island with the specified UUID.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to add as a member.
     * @param role       The role to assign to the member.
     * @return A CompletableFuture that completes when the member is added.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        return playerHandler.addMember(islandUuid, playerUuid, role);
    }

    /**
     * Removes a member from the island with the specified UUID.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to remove.
     * @return A CompletableFuture that completes when the member is removed.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        return playerHandler.removeMember(islandUuid, playerUuid);
    }

    /**
     * Sets a new owner for the island with the specified UUID.
     *
     * @param islandUuid         The UUID of the island.
     * @param newOwnerPlayerUuid The UUID of the player who will become the new owner.
     * @return A CompletableFuture that completes when the owner is set.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID newOwnerPlayerUuid) {
        return playerHandler.setOwner(islandUuid, newOwnerPlayerUuid);
    }

    /**
     * Sets the spawn location for the island with the specified player.
     *
     * @param playerUuid The UUID of the player.
     * @param location   The new spawn location for the island.
     * @return A CompletableFuture that completes when the spawn location is set.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, Location location) {
        return homeHandler.setHome(playerUuid, homeName, location);
    }

    /**
     * Deletes a home for the specified player.
     *
     * @param playerUuid The UUID of the player.
     * @param homeName   The name of the home to delete.
     * @return A CompletableFuture that completes when the home is deleted.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return homeHandler.delHome(playerUuid, homeName);
    }

    /**
     * Teleports a player to their home.
     *
     * @param playerUuid       The UUID of the player.
     * @param homeName         The name of the home to teleport to.
     * @param targetPlayerUuid The UUID of the player to teleport (can be null for self-teleport).
     * @return A CompletableFuture that completes when the teleport is done.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return homeHandler.home(playerUuid, homeName, targetPlayerUuid);
    }

    /**
     * Sets a warp point for the specified player.
     *
     * @param playerUuid The UUID of the player.
     * @param warpName   The name of the warp point.
     * @param location   The location of the warp point.
     * @return A CompletableFuture that completes when the warp is set.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, Location location) {
        return warpHandler.setWarp(playerUuid, warpName, location);
    }

    /**
     * Deletes a warp point for the specified player.
     *
     * @param playerUuid The UUID of the player.
     * @param warpName   The name of the warp point to delete.
     * @return A CompletableFuture that completes when the warp is deleted.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return warpHandler.delWarp(playerUuid, warpName);
    }

    /**
     * Teleports a player to a warp point.
     *
     * @param playerUuid       The UUID of the player.
     * @param warpName         The name of the warp point to teleport to.
     * @param targetPlayerUuid The UUID of the player to teleport (can be null for self-teleport).
     * @return A CompletableFuture that completes when the teleport is done.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> warp(UUID playerUuid, String warpName, UUID targetPlayerUuid) {
        return warpHandler.warp(playerUuid, warpName, targetPlayerUuid);
    }

    /**
     * Expels a player from the island.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to expel.
     * @return A CompletableFuture that completes when the player is expelled.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        return playerHandler.expelPlayer(islandUuid, playerUuid);
    }

    /**
     * Bans a player from the island.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to ban.
     * @return A CompletableFuture that completes when the player is banned.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID playerUuid) {
        return banHandler.banPlayer(islandUuid, playerUuid);
    }

    /**
     * Unbans a player from the island.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to unban.
     * @return A CompletableFuture that completes when the player is unbanned.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID playerUuid) {
        return banHandler.unbanPlayer(islandUuid, playerUuid);
    }

    /**
     * Adds a co-op player to the island.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to add as a co-op.
     * @return A CompletableFuture that completes when the co-op is added.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> addCoop(UUID islandUuid, UUID playerUuid) {
        return coopHandler.coopPlayer(islandUuid, playerUuid);
    }

    /**
     * Removes a co-op player from the island.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to remove from co-op.
     * @return A CompletableFuture that completes when the co-op is removed.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> removeCoop(UUID islandUuid, UUID playerUuid) {
        return coopHandler.unCoopPlayer(islandUuid, playerUuid);
    }

    /**
     * Removes all co-op players from the specified player's island.
     *
     * @param playerUuid The UUID of the player whose co-ops will be removed.
     */
    @SuppressWarnings("unused")
    public void removeAllCoopOfPlayer(UUID playerUuid) {
        coopHandler.deleteAllCoopOfPlayer(playerUuid);
    }

    /**
     * Toggles the lock status of the island.
     *
     * @param islandUuid The UUID of the island.
     * @return A CompletableFuture that completes with true if the island was locked, false if it was unlocked.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid) {
        return islandHandler.toggleIslandLock(islandUuid);
    }

    /**
     * Toggles the PvP status of the island.
     *
     * @param islandUuid The UUID of the island.
     * @return A CompletableFuture that completes with true if PvP was enabled, false if it was disabled.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid) {
        return islandHandler.toggleIslandPvp(islandUuid);
    }

    /**
     * Calculates the island level for the given island UUID asynchronously.
     *
     * @param islandUuid The UUID of the island to calculate level for.
     * @return A CompletableFuture that completes with the calculated level.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Integer> calIslandLevel(UUID islandUuid) {
        return levelHandler.calIslandLevel(islandUuid);
    }

    /**
     * Recalculates the block limits for the given island UUID asynchronously.
     *
     * @param islandUuid The UUID of the island to recalculate limits for.
     */
    @SuppressWarnings("unused")
    public void calIslandBlockCount(UUID islandUuid) {
        limitHandler.calIslandBlockCount(islandUuid);
    }

    /**
     * Gets the UUID of the island for a given player.
     *
     * @param playerUuid The UUID of the player.
     * @return The UUID of the island the player belongs to, or null if the player has no island.
     */
    @SuppressWarnings("unused")
    public UUID getIslandUuid(UUID playerUuid) {
        return islandHandler.getIslandUuid(playerUuid);
    }

    /**
     * Gets the UUID of the island owner
     *
     * @param islandUuid The UUID of the island.
     * @return The UUID of the owner of the island.
     */
    @SuppressWarnings("unused")
    public UUID getIslandOwner(UUID islandUuid) {
        return playerHandler.getIslandOwner(islandUuid);
    }

    /**
     * Get all the island members except the owner for a given island UUID.
     *
     * @param islandUuid The UUID of the island.
     * @return A set of UUIDs representing the members of the island.
     */
    @SuppressWarnings("unused")
    public Set<UUID> getIslandMembers(UUID islandUuid) {
        return playerHandler.getIslandMembers(islandUuid);
    }

    /**
     * Gets all players associated with a given island UUID.
     *
     * @param islandUuid The UUID of the island.
     * @return A set of UUIDs representing all players on the island.
     */
    @SuppressWarnings("unused")
    public Set<UUID> getIslandPlayers(UUID islandUuid) {
        return playerHandler.getIslandPlayers(islandUuid);
    }

    /**
     * Checks if the island is locked.
     *
     * @param islandUuid The UUID of the island.
     * @return true if the island is locked, false otherwise.
     */
    @SuppressWarnings("unused")
    public boolean isIslandLock(UUID islandUuid) {
        return islandHandler.isIslandLock(islandUuid);
    }

    /**
     * Checks if the island allows PvP.
     *
     * @param islandUuid The UUID of the island.
     * @return true if PvP is enabled on the island, false otherwise.
     */
    @SuppressWarnings("unused")
    public boolean isIslandPvp(UUID islandUuid) {
        return islandHandler.isIslandPvp(islandUuid);
    }

    /**
     * Gets the names of all homes for a given player UUID.
     *
     * @param playerUuid The UUID of the player.
     * @return A set of home names for the player.
     */
    @SuppressWarnings("unused")
    public Set<String> getHomeNames(UUID playerUuid) {
        return homeHandler.getHomeNames(playerUuid);
    }

    /**
     * Gets the names for all warps for a given player UUID.
     *
     * @param playerUuid The UUID of the player.
     * @return A set of warp names for the player.
     */
    @SuppressWarnings("unused")
    public Set<String> getWarpNames(UUID playerUuid) {
        return warpHandler.getWarpNames(playerUuid);
    }

    /**
     * Checks if a player is banned from the specified island.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to check.
     * @return true if the player is banned, false otherwise.
     */
    @SuppressWarnings("unused")
    public boolean isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        return banHandler.isPlayerBanned(islandUuid, playerUuid);
    }

    /**
     * Get the names of all players who are banned from the specified island.
     *
     * @param islandUuid The UUID of the island.
     * @return A set of UUIDs representing the banned players.
     */
    @SuppressWarnings("unused")
    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        return banHandler.getBannedPlayers(islandUuid);
    }

    /**
     * Checks if a player is cooped to the specified island.
     *
     * @param islandUuid The UUID of the island.
     * @param playerUuid The UUID of the player to check.
     * @return true if the player is cooped, false otherwise.
     */
    @SuppressWarnings("unused")
    public boolean isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return coopHandler.isPlayerCooped(islandUuid, playerUuid);
    }

    /**
     * Get the names of all players who are cooped to the specified island.
     *
     * @param islandUuid The UUID of the island.
     * @return A set of UUIDs representing the cooped players.
     */
    @SuppressWarnings("unused")
    public Set<UUID> getCoopedPlayers(UUID islandUuid) {
        return coopHandler.getCoopedPlayers(islandUuid);
    }

    /**
     * Get the island level for a given island UUID.
     *
     * @param islandUuid The UUID of the island.
     * @return The level of the island, or 0 if the island does not exist.
     */
    @SuppressWarnings("unused")
    public int getIslandLevel(UUID islandUuid) {
        return levelHandler.getIslandLevel(islandUuid);
    }

    /**
     * Gets the top island levels.
     *
     * @param size The number of top levels to retrieve.
     * @return A map of island UUIDs to their levels, sorted by level in descending order.
     */
    @SuppressWarnings("unused")
    public Map<UUID, Integer> getTopIslandLevels(int size) {
        return levelHandler.getTopIslandLevels(size);
    }

    /**
     * Adds a pending invite for the specified player.
     *
     * @param inviteeUuid UUID of the player receiving the invite.
     * @param islandUuid  UUID of the island offering the invite.
     * @param inviterUuid UUID of the inviter.
     * @param ttlSeconds  Time in seconds before the invite expires.
     * @return CompletableFuture that completes when the invite is added
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> addPendingInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        return playerHandler.addPendingInvite(inviteeUuid, islandUuid, inviterUuid, ttlSeconds);
    }

    /**
     * Removes a pending invite for a player.
     *
     * @param playerUuid UUID of the player whose invite should be removed.
     * @return CompletableFuture that completes when the invite is removed
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> removePendingInvite(UUID playerUuid) {
        return playerHandler.removePendingInvite(playerUuid);
    }

    /**
     * Gets the pending invite data for a player.
     *
     * @param playerUuid UUID of the player.
     * @return Optional containing Invitation if an invitation exists.
     */
    @SuppressWarnings("unused")
    public Optional<Invitation> getPendingInvite(UUID playerUuid) {
        return playerHandler.getPendingInvite(playerUuid);
    }

    /**
     * Teleports the player to the configured lobby server and location.
     *
     * @param playerUuid The UUID of the player to teleport.
     * @return A CompletableFuture that completes when the teleport is handled.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> lobby(UUID playerUuid) {
        return lobbyHandler.lobby(playerUuid);
    }

    /**
     * Sends a message to a player.
     *
     * @param playerUuid The UUID of the player to send the message to.
     * @param message    The message to send, as a Component.
     * @return A CompletableFuture that completes when the message is sent.
     */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> sendMessage(UUID playerUuid, Component message) {
        return messageHandler.sendMessage(playerUuid, message);
    }

    /**
     * Updates the UUID of a player in the database.
     *
     * @param uuid The new UUID of the player.
     * @param name The name of the player.
     */
    @SuppressWarnings("unused")
    public void updatePlayerUuid(UUID uuid, String name) {
        uuidHandler.updatePlayerUuid(uuid, name);
    }

    /**
     * Gets the UUID of a player by their name.
     *
     * @param name The name of the player.
     * @return An Optional containing the player's UUID if found, or empty if not found.
     */
    @SuppressWarnings("unused")
    public Optional<UUID> getPlayerUuid(String name) {
        return uuidHandler.getPlayerUuid(name);
    }

    /**
     * Gets the name of a player by their UUID.
     *
     * @param uuid The UUID of the player.
     * @return An Optional containing the player's name if found, or empty if not found.
     */
    @SuppressWarnings("unused")
    public Optional<String> getPlayerName(UUID uuid) {
        return uuidHandler.getPlayerName(uuid);
    }

    /**
     * Gets the online players' UUIDs in all sky block servers.
     *
     * @return A set of player UUIDs currently online.
     */
    @SuppressWarnings("unused")
    public Set<UUID> getOnlinePlayersUUIDs() {
        return plugin.getOnlinePlayersUUIDs();
    }

    /**
     * Gets the online players in all sky block servers.
     *
     * @return A set of player names currently online.
     */
    @SuppressWarnings("unused")
    public Set<String> getOnlinePlayersNames() {
        return plugin.getOnlinePlayersNames();
    }
}