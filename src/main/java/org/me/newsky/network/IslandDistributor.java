package org.me.newsky.network;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.ServerRegistry;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.exceptions.WrongIslandHostException;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.model.Actor;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.util.ServerUtil;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

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

    // Re-resolve a write when the island moved after routing but before execution.
    private static final int WRITE_ROUTE_ATTEMPTS = 3;

    private final NewSky plugin;
    private final IslandOperator islandOperator;
    private final ServerSelector serverSelector;
    private final ServerRegistry serverRegistry;
    private final IslandRegistry islandRegistry;
    private final CrossServerMessenger messenger;
    private final String serverID;

    public IslandDistributor(NewSky plugin, IslandOperator islandOperator, ServerSelector serverSelector,
                             ServerRegistry serverRegistry, IslandRegistry islandRegistry,
                             CrossServerMessenger messenger, String serverID) {
        this.plugin = plugin;
        this.islandOperator = islandOperator;
        this.serverSelector = serverSelector;
        this.serverRegistry = serverRegistry;
        this.islandRegistry = islandRegistry;
        this.messenger = messenger;
        this.serverID = serverID;
    }

    // =====================================================================================
    // Load routing
    // =====================================================================================

    private CompletableFuture<String> ensureIslandLoaded(UUID islandUuid) {
        String alreadyLoadedServer = getServerByIsland(islandUuid);
        if (alreadyLoadedServer != null) {
            return CompletableFuture.completedFuture(alreadyLoadedServer);
        }

        Map<String, String> activeServers = serverRegistry.getActiveGameServers();
        String candidate = selectClaimCapableServer(activeServers);
        if (candidate == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        String candidateInstance = activeServers.get(candidate);
        if (candidateInstance == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        IslandRegistry.HostClaim candidateClaim = new IslandRegistry.HostClaim(
                candidate, candidateInstance);
        boolean claimed = islandRegistry.claimHost(islandUuid, candidateClaim);
        String host = claimed ? candidate : getServerByIsland(islandUuid);
        if (host == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        return dispatchLoad(islandUuid, host).thenApply(v -> host);
    }

    // The host releases its own claim if loading fails. A remote timeout does not prove the host
    // stopped loading, so the distributor must not release that claim on its behalf.
    private CompletableFuture<Void> dispatchLoad(UUID islandUuid, String host) {
        if (host.equals(serverID)) {
            return islandOperator.loadIsland(islandUuid);
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        return messenger.requestVoid(host, ACTION_ISLAND_LOAD, payload);
    }

    // =====================================================================================
    // Island lifecycle
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        String targetServer = selectClaimCapableServer(serverRegistry.getActiveGameServers());
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
        Map<String, String> activeServers = serverRegistry.getActiveGameServers();
        String candidate = selectClaimCapableServer(activeServers);
        if (candidate == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        String candidateInstance = activeServers.get(candidate);
        if (candidateInstance == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        if (!islandRegistry.claimHost(islandUuid, new IslandRegistry.HostClaim(candidate, candidateInstance))) {
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }

        return dispatchLoad(islandUuid, candidate);
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
        JSONObject payload = islandActorPayload(islandUuid, actor);
        return routeWrite(islandUuid, ACTION_ISLAND_DELETE, payload,
                () -> islandOperator.deleteIsland(islandUuid, actor), response -> null);
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid,
                                                  String teleportWorld, String teleportLocation) {
        return ensureIslandLoaded(islandUuid).thenCompose(loadedServer -> {
            if (loadedServer.equals(serverID)) {
                return islandOperator.prepareTeleport(playerUuid, teleportWorld, teleportLocation);
            }

            JSONObject payload = new JSONObject();
            payload.put("playerUuid", playerUuid.toString());
            payload.put("teleportWorld", teleportWorld);
            payload.put("teleportLocation", teleportLocation);
            return messenger.requestVoid(loadedServer, ACTION_ISLAND_TELEPORT_PREPARE, payload)
                    .thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, loadedServer));
        });
    }

    public CompletableFuture<Void> teleportLobby(UUID playerUuid, List<String> lobbyServers,
                                                 String lobbyWorld, String lobbyLocation) {
        Map<String, String> availableLobbies = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : serverRegistry.getActiveServers().entrySet()) {
            if (lobbyServers.contains(entry.getKey())) {
                availableLobbies.put(entry.getKey(), entry.getValue());
            }
        }

        String lobbyServer = selectServer(availableLobbies);

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
        return messenger.requestVoid(lobbyServer, ACTION_ISLAND_TELEPORT_PREPARE, payload)
                .thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, lobbyServer));
    }

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role, UUID vouchedBy) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        payload.put("role", role);
        if (vouchedBy != null) {
            payload.put("vouchedBy", vouchedBy.toString());
        }
        return routeWrite(islandUuid, ACTION_ISLAND_MEMBER_ADD, payload,
                () -> islandOperator.addMember(islandUuid, playerUuid, role, vouchedBy), response -> null);
    }

    public CompletableFuture<Void> removeMember(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return routeWrite(islandUuid, ACTION_ISLAND_MEMBER_REMOVE, payload,
                () -> islandOperator.removeMember(islandUuid, actor, playerUuid), response -> null);
    }

    public CompletableFuture<Void> setOwner(UUID islandUuid, Actor actor, UUID newOwnerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("newOwnerUuid", newOwnerUuid.toString());
        return routeWrite(islandUuid, ACTION_ISLAND_OWNER_SET, payload,
                () -> islandOperator.setOwner(islandUuid, actor, newOwnerUuid), response -> null);
    }

    public CompletableFuture<Void> addBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return routeWrite(islandUuid, ACTION_ISLAND_BAN_ADD, payload,
                () -> islandOperator.addBan(islandUuid, actor, playerUuid), response -> null);
    }

    public CompletableFuture<Void> removeBan(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return routeWrite(islandUuid, ACTION_ISLAND_BAN_REMOVE, payload,
                () -> islandOperator.removeBan(islandUuid, actor, playerUuid), response -> null);
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return routeWrite(islandUuid, ACTION_ISLAND_COOP_ADD, payload,
                () -> islandOperator.addCoop(islandUuid, actor, playerUuid), response -> null);
    }

    public CompletableFuture<Void> removeCoop(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());
        return routeWrite(islandUuid, ACTION_ISLAND_COOP_REMOVE, payload,
                () -> islandOperator.removeCoop(islandUuid, actor, playerUuid), response -> null);
    }

    public CompletableFuture<Boolean> toggleLock(UUID islandUuid, Actor actor) {
        UUID operationId = UUID.randomUUID();
        JSONObject payload = islandActorPayload(islandUuid, actor).put("operationId", operationId.toString());
        return routeWrite(islandUuid, ACTION_ISLAND_LOCK_TOGGLE, payload,
                () -> islandOperator.toggleLock(islandUuid, actor, operationId),
                response -> response.getBoolean("locked"));
    }

    public CompletableFuture<Boolean> togglePvp(UUID islandUuid, Actor actor) {
        UUID operationId = UUID.randomUUID();
        JSONObject payload = islandActorPayload(islandUuid, actor).put("operationId", operationId.toString());
        return routeWrite(islandUuid, ACTION_ISLAND_PVP_TOGGLE, payload,
                () -> islandOperator.togglePvp(islandUuid, actor, operationId), response -> response.getBoolean("pvp"));
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, Actor actor, UUID playerUuid) {
        JSONObject payload = islandActorPayload(islandUuid, actor);
        payload.put("playerUuid", playerUuid.toString());

        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null || islandServer.equals(serverID)) {
            return islandOperator.expelPlayer(islandUuid, actor, playerUuid);
        }

        return messenger.requestVoid(islandServer, ACTION_ISLAND_EXPEL, payload);
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

    private <T> CompletableFuture<T> routeWrite(UUID islandUuid, String action, JSONObject payload,
                                                 Supplier<CompletableFuture<T>> localOperation,
                                                 Function<JSONObject, T> remoteResult) {
        return routeWrite(islandUuid, action, payload, localOperation, remoteResult, 1);
    }

    private <T> CompletableFuture<T> routeWrite(UUID islandUuid, String action, JSONObject payload,
                                                 Supplier<CompletableFuture<T>> localOperation,
                                                 Function<JSONObject, T> remoteResult, int attempt) {
        String islandServer = getServerByIsland(islandUuid);

        CompletableFuture<T> write;
        if (islandServer == null || islandServer.equals(serverID)) {
            write = localOperation.get();
        } else {
            write = messenger.request(islandServer, action, payload).thenApply(remoteResult);
        }

        return write.exceptionallyCompose(error -> {
            if (unwrap(error) instanceof WrongIslandHostException && attempt < WRITE_ROUTE_ATTEMPTS) {
                return routeWrite(islandUuid, action, payload, localOperation, remoteResult, attempt + 1);
            }
            return CompletableFuture.failedFuture(error);
        });
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String selectServer(Map<String, String> servers) {
        return serverSelector.selectServer(servers);
    }

    private String selectClaimCapableServer(Map<String, String> servers) {
        Map<String, String> incarnationAware = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : servers.entrySet()) {
            try {
                UUID.fromString(entry.getValue());
                incarnationAware.put(entry.getKey(), entry.getValue());
            } catch (RuntimeException legacyHeartbeat) {
                // Old heartbeat values cannot safely own incarnation-fenced claims.
            }
        }

        return selectServer(incarnationAware);
    }

    private String getServerByIsland(UUID islandUuid) {
        return islandRegistry.getHost(islandUuid).orElse(null);
    }
}
