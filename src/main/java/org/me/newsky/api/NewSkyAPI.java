package org.me.newsky.api;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.island.*;
import org.me.newsky.message.PlayerMessageHandler;
import org.me.newsky.model.Invitation;
import org.me.newsky.model.IslandTop;
import org.me.newsky.uuid.UuidHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * The plugin's public surface. One rule, no exceptions:
 * <ul>
 *   <li><b>Writes go through {@link #player(UUID)} or {@link #admin(CommandSender)}</b>. The
 *       player handle acts as that player only, on that player's own island only - anything else
 *       is unrepresentable, not merely checked. The admin handle acts on arbitrary targets and
 *       carries the sender's name into logs and cross-server payloads.</li>
 *   <li><b>Reads live on this class</b> - island state is not secret, and a read changes
 *       nothing.</li>
 * </ul>
 * Internal machinery (quit cleanup, schedulers, join listeners) does not pass through this API:
 * it calls its handler directly. What you see here is exactly the surface commands and other
 * plugins are meant to use.
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
    // Write entry points
    // ================================================================================================================

    /** Act as this player: own identity, own island, player rules apply. */
    @SuppressWarnings("unused")
    public PlayerActions player(UUID playerUuid) {
        return new PlayerActions(playerUuid, coreHandler, playerHandler, homeHandler, warpHandler, banHandler, coopHandler, biomeHandler);
    }

    /** Act as an operator: arbitrary targets, no player rules, the sender's name goes to logs. */
    @SuppressWarnings("unused")
    public AdminActions admin(CommandSender sender) {
        return new AdminActions(sender.getName(), coreHandler, playerHandler, homeHandler, warpHandler, banHandler, coopHandler, biomeHandler);
    }

    // ================================================================================================================
    // Reads
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
    public CompletableFuture<Optional<Invitation>> getInvite(UUID playerUuid) {
        return playerHandler.getInvite(playerUuid);
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
    public CompletableFuture<Set<UUID>> getIslandBans(UUID islandUuid) {
        return banHandler.getIslandBans(islandUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return coopHandler.isPlayerCooped(islandUuid, playerUuid);
    }

    @SuppressWarnings("unused")
    public CompletableFuture<Set<UUID>> getIslandCoops(UUID islandUuid) {
        return coopHandler.getIslandCoops(islandUuid);
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
    // Live-player actions - no island state, so neither handle applies
    // ================================================================================================================

    /** Moves a player to a lobby server. Touches no island state; also the fail-closed bounce. */
    @SuppressWarnings("unused")
    public CompletableFuture<Void> lobby(UUID playerUuid) {
        return lobbyHandler.lobby(playerUuid);
    }

    @SuppressWarnings("unused")
    public void sendPlayerMessage(UUID playerUuid, Component message) {
        playerMessageHandler.sendPlayerMessage(playerUuid, message);
    }
}
