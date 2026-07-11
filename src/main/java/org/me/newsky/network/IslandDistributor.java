package org.me.newsky.network;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.lock.IslandOperationLock;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.state.IslandServerState;
import org.me.newsky.state.ServerHeartbeatState;
import org.me.newsky.util.IslandUtils;
import org.me.newsky.util.ServerUtil;
import org.me.newsky.world.WorldHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class IslandDistributor {

    public static final String ACTION_ISLAND_CREATE = "island.create";
    public static final String ACTION_ISLAND_LOAD = "island.load";
    public static final String ACTION_ISLAND_UNLOAD = "island.unload";
    public static final String ACTION_ISLAND_DELETE = "island.delete";
    public static final String ACTION_ISLAND_TELEPORT_PREPARE = "island.teleport.prepare";
    public static final String ACTION_ISLAND_MEMBER_ADD = "island.member.add";
    public static final String ACTION_ISLAND_MEMBER_REMOVE = "island.member.remove";
    public static final String ACTION_ISLAND_OWNER_SET = "island.owner.set";
    public static final String ACTION_ISLAND_BAN_ADD = "island.ban.add";
    public static final String ACTION_ISLAND_BAN_REMOVE = "island.ban.remove";
    public static final String ACTION_ISLAND_COOP_ADD = "island.coop.add";
    public static final String ACTION_ISLAND_COOP_REMOVE = "island.coop.remove";
    public static final String ACTION_ISLAND_LOCK_SET = "island.lock.set";
    public static final String ACTION_ISLAND_PVP_SET = "island.pvp.set";
    public static final String ACTION_ISLAND_UPGRADE_SET = "island.upgrade.set";
    public static final String ACTION_ISLAND_EXPEL = "island.expel";

    private final NewSky plugin;
    private final IslandOperator islandOperator;
    private final IslandOperationLock islandOperationLock;
    private final ServerSelector serverSelector;
    private final ServerHeartbeatState serverHeartbeatState;
    private final IslandServerState islandServerState;
    private final CrossServerMessenger messenger;
    private final WorldHandler worldHandler;
    private final String serverID;

    public IslandDistributor(NewSky plugin, IslandOperator islandOperator, IslandOperationLock islandOperationLock, ServerSelector serverSelector, ServerHeartbeatState serverHeartbeatState, IslandServerState islandServerState, CrossServerMessenger messenger, WorldHandler worldHandler, String serverID) {
        this.plugin = plugin;
        this.islandOperator = islandOperator;
        this.islandOperationLock = islandOperationLock;
        this.serverSelector = serverSelector;
        this.serverHeartbeatState = serverHeartbeatState;
        this.islandServerState = islandServerState;
        this.messenger = messenger;
        this.worldHandler = worldHandler;
        this.serverID = serverID;
    }

    // =====================================================================================
    // High-level reusable primitive
    // =====================================================================================

    private CompletableFuture<String> ensureIslandLoaded(UUID islandUuid) {
        String alreadyLoadedServer = getServerByIsland(islandUuid);
        if (alreadyLoadedServer != null) {
            if (islandOperationLock.isLocked(islandUuid)) {
                return CompletableFuture.failedFuture(new IslandBusyException());
            }
            return CompletableFuture.completedFuture(alreadyLoadedServer);
        }

        return islandOperationLock.withLock(islandUuid, () -> {
            String recheckedServer = getServerByIsland(islandUuid);
            if (recheckedServer != null) {
                return CompletableFuture.completedFuture(recheckedServer);
            }

            String targetServer = selectServer(serverHeartbeatState.getActiveGameServers());
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            if (targetServer.equals(serverID)) {
                return islandOperator.loadIsland(islandUuid).thenApply(v -> targetServer);
            }

            JSONObject payload = new JSONObject();
            payload.put("islandUuid", islandUuid.toString());
            return messenger.requestVoid(targetServer, ACTION_ISLAND_LOAD, payload).thenApply(v -> targetServer);
        });
    }

    // =====================================================================================
    // Island lifecycle
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid, String homeLocation) {
        return islandOperationLock.withLock(islandUuid, () -> {
            String targetServer = selectServer(serverHeartbeatState.getActiveGameServers());
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            JSONObject payload = new JSONObject();
            payload.put("islandUuid", islandUuid.toString());
            payload.put("ownerUuid", ownerUuid.toString());
            payload.put("homeLocation", homeLocation);

            if (targetServer.equals(serverID)) {
                return islandOperator.createIsland(islandUuid, ownerUuid, homeLocation);
            }

            return messenger.requestVoid(targetServer, ACTION_ISLAND_CREATE, payload);
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return islandOperationLock.withLock(islandUuid, () -> {
            String islandServer = getServerByIsland(islandUuid);
            if (islandServer != null) {
                return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
            }

            String targetServer = selectServer(serverHeartbeatState.getActiveGameServers());
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            if (targetServer.equals(serverID)) {
                return islandOperator.loadIsland(islandUuid);
            }

            JSONObject payload = new JSONObject();
            payload.put("islandUuid", islandUuid.toString());
            return messenger.requestVoid(targetServer, ACTION_ISLAND_LOAD, payload);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return islandOperationLock.withLock(islandUuid, () -> {
            String islandServer = getServerByIsland(islandUuid);
            if (islandServer == null) {
                return CompletableFuture.failedFuture(new IslandNotLoadedException());
            }

            if (islandServer.equals(serverID)) {
                return islandOperator.unloadIsland(islandUuid);
            }

            JSONObject payload = new JSONObject();
            payload.put("islandUuid", islandUuid.toString());
            return messenger.requestVoid(islandServer, ACTION_ISLAND_UNLOAD, payload);
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return islandOperationLock.withLock(islandUuid, () -> {
            String islandServer = getServerByIsland(islandUuid);
            if (islandServer == null || islandServer.equals(serverID)) {
                return islandOperator.deleteIsland(islandUuid);
            }

            JSONObject payload = new JSONObject();
            payload.put("islandUuid", islandUuid.toString());
            return messenger.requestVoid(islandServer, ACTION_ISLAND_DELETE, payload);
        });
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportWorld, String teleportLocation) {
        return ensureIslandLoaded(islandUuid).thenCompose(loadedServer -> {
            if (loadedServer.equals(serverID)) {
                return islandOperator.prepareTeleport(playerUuid, teleportWorld, teleportLocation);
            }

            JSONObject payload = new JSONObject();
            payload.put("playerUuid", playerUuid.toString());
            payload.put("teleportWorld", teleportWorld);
            payload.put("teleportLocation", teleportLocation);
            return messenger.requestVoid(loadedServer, ACTION_ISLAND_TELEPORT_PREPARE, payload).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, loadedServer));
        });
    }

    public CompletableFuture<Void> teleportLobby(UUID playerUuid, List<String> lobbyServers, String lobbyWorld, String lobbyLocation) {
        String lobbyServer = selectServer(serverHeartbeatState.getActiveServers().entrySet().stream().filter(entry -> lobbyServers.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        if (lobbyServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        if (lobbyServer.equals(serverID)) {
            return islandOperator.prepareTeleport(playerUuid, lobbyWorld, lobbyLocation);
        }

        JSONObject payload = new JSONObject();
        payload.put("playerUuid", playerUuid.toString());
        payload.put("teleportWorld", lobbyWorld);
        payload.put("teleportLocation", lobbyLocation);
        return messenger.requestVoid(lobbyServer, ACTION_ISLAND_TELEPORT_PREPARE, payload).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, lobbyServer));
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role, String homeLocation) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        payload.put("role", role);
        payload.put("homeLocation", homeLocation);
        return runOnIslandServer(islandUuid, ACTION_ISLAND_MEMBER_ADD, payload, () -> {
            return islandOperator.addMember(islandUuid, playerUuid, role, homeLocation);
        });
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, UUID playerUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_MEMBER_REMOVE, payload, () -> islandOperator.removeMember(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, UUID oldOwnerUuid, UUID newOwnerUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("oldOwnerUuid", oldOwnerUuid.toString());
        payload.put("newOwnerUuid", newOwnerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_OWNER_SET, payload, () -> islandOperator.setOwner(islandUuid, oldOwnerUuid, newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(UUID islandUuid, UUID playerUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_BAN_ADD, payload, () -> islandOperator.addBan(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> removeBan(UUID islandUuid, UUID playerUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_BAN_REMOVE, payload, () -> islandOperator.removeBan(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, UUID playerUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_COOP_ADD, payload, () -> islandOperator.addCoop(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, UUID playerUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_COOP_REMOVE, payload, () -> islandOperator.removeCoop(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> setIslandLock(UUID islandUuid, boolean locked) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("locked", locked);
        return runOnIslandServer(islandUuid, ACTION_ISLAND_LOCK_SET, payload, () -> islandOperator.setIslandLock(islandUuid, locked));
    }

    public CompletableFuture<Void> setIslandPvp(UUID islandUuid, boolean pvp) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("pvp", pvp);
        return runOnIslandServer(islandUuid, ACTION_ISLAND_PVP_SET, payload, () -> islandOperator.setIslandPvp(islandUuid, pvp));
    }

    public CompletableFuture<Void> setUpgradeLevel(UUID islandUuid, String upgradeId, int level, int borderSize) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("upgradeId", upgradeId);
        payload.put("level", level);
        payload.put("borderSize", borderSize);
        return runOnIslandServer(islandUuid, ACTION_ISLAND_UPGRADE_SET, payload, () -> islandOperator.setUpgradeLevel(islandUuid, upgradeId, level, borderSize));
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_EXPEL, payload, () -> worldHandler.removePlayerFromWorld(IslandUtils.UUIDToName(islandUuid), playerUuid));
    }

    // =====================================================================================
    // Internal helpers
    // =====================================================================================

    private CompletableFuture<Void> runOnIslandServer(UUID islandUuid, String action, JSONObject payload, LocalOperation localOperation) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null || islandServer.equals(serverID)) {
            return localOperation.run();
        }

        return messenger.requestVoid(islandServer, action, payload);
    }

    private String selectServer(Map<String, String> servers) {
        return serverSelector.selectServer(servers);
    }

    private String getServerByIsland(UUID islandUuid) {
        return islandServerState.getIslandLoadedServer(islandUuid).orElse(null);
    }

    @FunctionalInterface
    private interface LocalOperation {
        CompletableFuture<Void> run();
    }
}
