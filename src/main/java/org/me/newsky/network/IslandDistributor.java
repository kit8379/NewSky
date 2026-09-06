package org.me.newsky.network;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.cluster.ServerRegistry;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.model.Actor;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.util.ServerUtil;

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
    public static final String ACTION_ISLAND_LOCK_TOGGLE = "island.lock.toggle";
    public static final String ACTION_ISLAND_PVP_TOGGLE = "island.pvp.toggle";
    public static final String ACTION_ISLAND_EXPEL = "island.expel";
    public static final String ACTION_PLAYER_CONNECT = "player.connect";

    private final NewSky plugin;
    private final IslandOperator islandOperator;
    private final ServerSelector serverSelector;
    private final ServerRegistry serverRegistry;
    private final IslandRegistry islandRegistry;
    private final OnlinePlayerRegistry onlinePlayerRegistry;
    private final CrossServerMessenger messenger;
    private final String serverID;

    public IslandDistributor(NewSky plugin, IslandOperator islandOperator, ServerSelector serverSelector, ServerRegistry serverRegistry, IslandRegistry islandRegistry, OnlinePlayerRegistry onlinePlayerRegistry, CrossServerMessenger messenger, String serverID) {
        this.plugin = plugin;
        this.islandOperator = islandOperator;
        this.serverSelector = serverSelector;
        this.serverRegistry = serverRegistry;
        this.islandRegistry = islandRegistry;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
        this.messenger = messenger;
        this.serverID = serverID;
    }

    // =====================================================================================
    // High-level reusable primitive
    // =====================================================================================

    private CompletableFuture<String> ensureIslandLoaded(UUID islandUuid) {
        String alreadyLoadedServer = getServerByIsland(islandUuid);
        if (alreadyLoadedServer != null) {
            return CompletableFuture.completedFuture(alreadyLoadedServer);
        }

        String targetServer = selectServer(serverRegistry.getActiveGameServers());
        if (targetServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        return loadIslandOnServer(islandUuid, targetServer).thenApply(v -> targetServer);
    }

    private CompletableFuture<Void> loadIslandOnServer(UUID islandUuid, String targetServer) {
        if (targetServer.equals(serverID)) {
            return islandOperator.loadIsland(islandUuid);
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        return messenger.requestVoid(targetServer, ACTION_ISLAND_LOAD, payload);
    }

    // =====================================================================================
    // Island lifecycle
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        String targetServer = selectServer(serverRegistry.getActiveGameServers());
        if (targetServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        if (islandRegistry.getIslandLoadedServer(islandUuid).isPresent()) {
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("ownerUuid", ownerUuid.toString());

        if (targetServer.equals(serverID)) {
            return islandOperator.createIsland(islandUuid, ownerUuid);
        }

        return messenger.requestVoid(targetServer, ACTION_ISLAND_CREATE, payload);
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String targetServer = selectServer(serverRegistry.getActiveGameServers());
        if (targetServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        if (islandRegistry.getIslandLoadedServer(islandUuid).isPresent()) {
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }

        return loadIslandOnServer(islandUuid, targetServer);
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
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
    }

    public CompletableFuture<Void> deleteIsland(Actor actor, UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null || islandServer.equals(serverID)) {
            return islandOperator.deleteIsland(actor, islandUuid);
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put(Actor.FIELD, Actor.toJson(actor));
        return messenger.requestVoid(islandServer, ACTION_ISLAND_DELETE, payload);
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportWorld, String teleportLocation) {
        return ensureIslandLoaded(islandUuid).thenCompose(loadedServer -> {
            return teleportViaServer(loadedServer, playerUuid, teleportWorld, teleportLocation);
        });
    }

    public CompletableFuture<Void> teleportLobby(UUID playerUuid, List<String> lobbyServers, String lobbyWorld, String lobbyLocation) {
        String lobbyServer = selectServer(serverRegistry.getActiveServers().entrySet().stream().filter(entry -> lobbyServers.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        if (lobbyServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        return teleportViaServer(lobbyServer, playerUuid, lobbyWorld, lobbyLocation);
    }

    private CompletableFuture<Void> teleportViaServer(String hostServer, UUID playerUuid, String teleportWorld, String teleportLocation) {
        CompletableFuture<Boolean> prepared;
        if (hostServer.equals(serverID)) {
            prepared = islandOperator.prepareTeleport(playerUuid, teleportWorld, teleportLocation);
        } else {
            JSONObject payload = new JSONObject();
            payload.put("playerUuid", playerUuid.toString());
            payload.put("teleportWorld", teleportWorld);
            payload.put("teleportLocation", teleportLocation);
            prepared = messenger.request(hostServer, ACTION_ISLAND_TELEPORT_PREPARE, payload).thenApply(resp -> resp.getBoolean("teleported"));
        }

        return prepared.thenCompose(teleported -> {
            // Teleported in place by the prepare step: nothing left to route.
            if (teleported) {
                return CompletableFuture.completedFuture(null);
            }

            // The registry read must stay off the main thread; a local prepare completes there.
            return CompletableFuture.completedFuture(null).thenComposeAsync(v -> {
                String playerServer = onlinePlayerRegistry.getOnlinePlayerServer(playerUuid);

                // Already on the host (arrived since the prepare step): nothing to do.
                // Offline: the pending teleport fires on their next join to the host.
                if (playerServer == null || playerServer.equals(hostServer)) {
                    return CompletableFuture.completedFuture(null);
                }

                if (playerServer.equals(serverID)) {
                    return ServerUtil.connectToServer(plugin, playerUuid, hostServer);
                }

                JSONObject payload = new JSONObject();
                payload.put("playerUuid", playerUuid.toString());
                payload.put("targetServer", hostServer);
                return messenger.requestVoid(playerServer, ACTION_PLAYER_CONNECT, payload);
            }, plugin.getBukkitAsyncExecutor());
        });
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        payload.put("role", role);
        return runOnIslandServer(islandUuid, ACTION_ISLAND_MEMBER_ADD, payload, () -> {
            return islandOperator.addMember(islandUuid, playerUuid, role);
        });
    }

    public CompletableFuture<Void> removeMember(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_MEMBER_REMOVE, payload, () -> islandOperator.removeMember(actor, islandUuid, playerUuid));
    }

    public CompletableFuture<Void> setOwner(Actor actor, UUID islandUuid, UUID newOwnerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("newOwnerUuid", newOwnerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_OWNER_SET, payload, () -> islandOperator.setOwner(actor, islandUuid, newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_BAN_ADD, payload, () -> islandOperator.addBan(actor, islandUuid, playerUuid));
    }

    public CompletableFuture<Void> removeBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_BAN_REMOVE, payload, () -> islandOperator.removeBan(actor, islandUuid, playerUuid));
    }

    public CompletableFuture<Void> addCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_COOP_ADD, payload, () -> islandOperator.addCoop(actor, islandUuid, playerUuid));
    }

    public CompletableFuture<Void> removeCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_COOP_REMOVE, payload, () -> islandOperator.removeCoop(actor, islandUuid, playerUuid));
    }

    public CompletableFuture<Boolean> toggleIslandLock(Actor actor, UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null || islandServer.equals(serverID)) {
            return islandOperator.toggleIslandLock(actor, islandUuid);
        }

        return messenger.request(islandServer, ACTION_ISLAND_LOCK_TOGGLE, islandActorPayload(actor, islandUuid)).thenApply(resp -> resp.getBoolean("locked"));
    }

    public CompletableFuture<Boolean> toggleIslandPvp(Actor actor, UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null || islandServer.equals(serverID)) {
            return islandOperator.toggleIslandPvp(actor, islandUuid);
        }

        return messenger.request(islandServer, ACTION_ISLAND_PVP_TOGGLE, islandActorPayload(actor, islandUuid)).thenApply(resp -> resp.getBoolean("pvp"));
    }

    public CompletableFuture<Void> expelPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_EXPEL, payload, () -> islandOperator.expelPlayer(actor, islandUuid, playerUuid));
    }

    // =====================================================================================
    // Internal helpers
    // =====================================================================================

    private JSONObject islandActorPayload(Actor actor, UUID islandUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put(Actor.FIELD, Actor.toJson(actor));
        return payload;
    }

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
        return islandRegistry.getIslandLoadedServer(islandUuid).orElse(null);
    }

    @FunctionalInterface
    private interface LocalOperation {
        CompletableFuture<Void> run();
    }
}
