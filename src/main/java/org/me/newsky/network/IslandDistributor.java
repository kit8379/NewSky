package org.me.newsky.network;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.model.Actor;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.ServerRegistry;
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
    public static final String ACTION_ISLAND_SNAPSHOT_REFRESH = "island.snapshot.refresh";

    private final NewSky plugin;
    private final IslandOperator islandOperator;
    private final ServerSelector serverSelector;
    private final ServerRegistry serverRegistry;
    private final IslandRegistry islandRegistry;
    private final CrossServerMessenger messenger;
    private final String serverID;

    public IslandDistributor(NewSky plugin, IslandOperator islandOperator, ServerSelector serverSelector, ServerRegistry serverRegistry, IslandRegistry islandRegistry, CrossServerMessenger messenger, String serverID) {
        this.plugin = plugin;
        this.islandOperator = islandOperator;
        this.serverSelector = serverSelector;
        this.serverRegistry = serverRegistry;
        this.islandRegistry = islandRegistry;
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

        String candidate = selectServer(serverRegistry.getActiveGameServers());
        if (candidate == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        // Claim the host before loading. Without the claim two servers can both observe an unclaimed
        // island, pick different hosts, and load the same world twice on top of one storage backend.
        boolean claimed = islandRegistry.claimIslandLoadedServer(islandUuid, candidate);
        String host = claimed ? candidate : getServerByIsland(islandUuid);
        if (host == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        // Winner and losers alike dispatch the load to the claimed host, which de-duplicates them
        // locally. That way every caller only continues once the world is really loaded.
        return dispatchLoad(islandUuid, host, claimed).thenApply(v -> host);
    }

    private CompletableFuture<Void> dispatchLoad(UUID islandUuid, String host, boolean claimedByUs) {
        CompletableFuture<Void> load;

        if (host.equals(serverID)) {
            load = islandOperator.loadIsland(islandUuid);
        } else {
            JSONObject payload = new JSONObject();
            payload.put("islandUuid", islandUuid.toString());
            load = messenger.requestVoid(host, ACTION_ISLAND_LOAD, payload);
        }

        return load.exceptionallyCompose(e -> {
            // Only the caller that took the claim may release it, otherwise a failing loser would
            // strip the claim out from under the winner while it is still loading.
            if (claimedByUs) {
                islandRegistry.removeIslandLoadedServer(islandUuid);
            }
            return CompletableFuture.failedFuture(e);
        });
    }

    // =====================================================================================
    // Island lifecycle
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        String targetServer = selectServer(serverRegistry.getActiveGameServers());
        if (targetServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
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
        String candidate = selectServer(serverRegistry.getActiveGameServers());
        if (candidate == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        // Losing the claim is exactly the "already loaded" case, and deciding it this way makes two
        // simultaneous load requests resolve atomically instead of both proceeding.
        if (!islandRegistry.claimIslandLoadedServer(islandUuid, candidate)) {
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }

        return dispatchLoad(islandUuid, candidate, true);
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

    public CompletableFuture<Void> deleteIsland(UUID islandUuid, Actor actor) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null || islandServer.equals(serverID)) {
            return islandOperator.deleteIsland(islandUuid, actor);
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put(Actor.FIELD, Actor.toJson(actor));
        return messenger.requestVoid(islandServer, ACTION_ISLAND_DELETE, payload);
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
        String lobbyServer = selectServer(serverRegistry.getActiveServers().entrySet().stream().filter(entry -> lobbyServers.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

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

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        payload.put("role", role);
        return runOnIslandServer(islandUuid, ACTION_ISLAND_MEMBER_ADD, payload, () -> {
            return islandOperator.addMember(islandUuid, playerUuid, role);
        });
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_MEMBER_REMOVE, payload, () -> islandOperator.removeMember(islandUuid, actor, playerUuid));
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, Actor actor, UUID newOwnerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("newOwnerUuid", newOwnerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_OWNER_SET, payload, () -> islandOperator.setOwner(islandUuid, actor, newOwnerUuid));
    }

    public CompletableFuture<Void> addBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_BAN_ADD, payload, () -> islandOperator.addBan(islandUuid, actor, playerUuid));
    }

    public CompletableFuture<Void> removeBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_BAN_REMOVE, payload, () -> islandOperator.removeBan(islandUuid, actor, playerUuid));
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_COOP_ADD, payload, () -> islandOperator.addCoop(islandUuid, actor, playerUuid));
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_COOP_REMOVE, payload, () -> islandOperator.removeCoop(islandUuid, actor, playerUuid));
    }

    public CompletableFuture<Boolean> toggleIslandLock(UUID islandUuid, Actor actor) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null || islandServer.equals(serverID)) {
            return islandOperator.toggleIslandLock(islandUuid, actor);
        }

        return messenger.request(islandServer, ACTION_ISLAND_LOCK_TOGGLE, islandActorPayload(islandUuid, actor)).thenApply(resp -> resp.getBoolean("locked"));
    }

    public CompletableFuture<Boolean> toggleIslandPvp(UUID islandUuid, Actor actor) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null || islandServer.equals(serverID)) {
            return islandOperator.toggleIslandPvp(islandUuid, actor);
        }

        return messenger.request(islandServer, ACTION_ISLAND_PVP_TOGGLE, islandActorPayload(islandUuid, actor)).thenApply(resp -> resp.getBoolean("pvp"));
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        return runOnIslandServer(islandUuid, ACTION_ISLAND_EXPEL, payload, () -> islandOperator.expelPlayer(islandUuid, playerUuid));
    }

    /**
     * Refreshes the island's snapshot on whichever server hosts it. A no-op for unloaded islands:
     * they have no snapshot anywhere.
     */
    public CompletableFuture<Void> refreshIslandSnapshot(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            return CompletableFuture.completedFuture(null);
        }

        if (islandServer.equals(serverID)) {
            return islandOperator.refreshSnapshot(islandUuid);
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        return messenger.requestVoid(islandServer, ACTION_ISLAND_SNAPSHOT_REFRESH, payload);
    }

    // =====================================================================================
    // Internal helpers
    // =====================================================================================

    private JSONObject islandActorPayload(UUID islandUuid, Actor actor) {
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
