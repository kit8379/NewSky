package org.me.newsky.network;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.cluster.ServerRegistry;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.exceptions.PlayerNotInIslandException;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.model.Actor;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.util.ServerUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Routes island work to the server holding the island's claim. Reads follow the claim;
 * anything that changes island state either runs on the host or, for an unloaded island,
 * under a transient claim of its own, so no mutation ever runs without the lock.
 */
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

    private static final Function<JSONObject, Void> VOID = response -> null;

    private final NewSky plugin;
    private final IslandOperator islandOperator;
    private final ServerSelector serverSelector;
    private final ServerRegistry serverRegistry;
    private final IslandRegistry islandRegistry;
    private final IslandClaims islandClaims;
    private final OnlinePlayerRegistry onlinePlayerRegistry;
    private final CrossServerMessenger messenger;
    private final String serverID;

    public IslandDistributor(NewSky plugin, IslandOperator islandOperator, ServerSelector serverSelector, ServerRegistry serverRegistry, IslandRegistry islandRegistry, IslandClaims islandClaims, OnlinePlayerRegistry onlinePlayerRegistry, CrossServerMessenger messenger, String serverID) {
        this.plugin = plugin;
        this.islandOperator = islandOperator;
        this.serverSelector = serverSelector;
        this.serverRegistry = serverRegistry;
        this.islandRegistry = islandRegistry;
        this.islandClaims = islandClaims;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
        this.messenger = messenger;
        this.serverID = serverID;
    }

    // =====================================================================================
    // Island lifecycle
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        String targetServer = selectServer(serverRegistry.getActiveGameServers());
        if (targetServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        if (targetServer.equals(serverID)) {
            return islandOperator.createIsland(islandUuid, ownerUuid);
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("ownerUuid", ownerUuid.toString());
        return messenger.requestVoid(targetServer, ACTION_ISLAND_CREATE, payload);
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return supply(() -> {
            if (resolveHost(islandUuid) != null) {
                return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
            }

            String targetServer = selectServer(serverRegistry.getActiveGameServers());
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            return loadIslandOnServer(islandUuid, targetServer).thenApply(host -> null);
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return supply(() -> {
            String host = resolveHost(islandUuid);
            if (host == null) {
                return CompletableFuture.failedFuture(new IslandNotLoadedException());
            }

            if (host.equals(serverID)) {
                return islandOperator.unloadIsland(islandUuid);
            }

            JSONObject payload = new JSONObject();
            payload.put("islandUuid", islandUuid.toString());
            return messenger.requestVoid(host, ACTION_ISLAND_UNLOAD, payload);
        });
    }

    public CompletableFuture<Void> deleteIsland(Actor actor, UUID islandUuid) {
        return onIsland(islandUuid, ACTION_ISLAND_DELETE, islandActorPayload(actor, islandUuid), () -> islandOperator.deleteIsland(actor, islandUuid), VOID);
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportWorld, String teleportLocation) {
        return retryingStaleRouting(() -> ensureIslandLoaded(islandUuid).thenCompose(host -> teleportViaServer(host, playerUuid, teleportWorld, teleportLocation)));
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

    // =====================================================================================
    // Island mutations
    // =====================================================================================

    public CompletableFuture<Void> addMember(UUID islandUuid, UUID playerUuid, String role) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("playerUuid", playerUuid.toString());
        payload.put("role", role);
        return onIsland(islandUuid, ACTION_ISLAND_MEMBER_ADD, payload, () -> islandOperator.addMember(islandUuid, playerUuid, role), VOID);
    }

    public CompletableFuture<Void> removeMember(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return onIsland(islandUuid, ACTION_ISLAND_MEMBER_REMOVE, payload, () -> islandOperator.removeMember(actor, islandUuid, playerUuid), VOID);
    }

    public CompletableFuture<Void> setOwner(Actor actor, UUID islandUuid, UUID newOwnerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("newOwnerUuid", newOwnerUuid.toString());
        return onIsland(islandUuid, ACTION_ISLAND_OWNER_SET, payload, () -> islandOperator.setOwner(actor, islandUuid, newOwnerUuid), VOID);
    }

    public CompletableFuture<Void> addBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return onIsland(islandUuid, ACTION_ISLAND_BAN_ADD, payload, () -> islandOperator.addBan(actor, islandUuid, playerUuid), VOID);
    }

    public CompletableFuture<Void> removeBan(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return onIsland(islandUuid, ACTION_ISLAND_BAN_REMOVE, payload, () -> islandOperator.removeBan(actor, islandUuid, playerUuid), VOID);
    }

    public CompletableFuture<Void> addCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return onIsland(islandUuid, ACTION_ISLAND_COOP_ADD, payload, () -> islandOperator.addCoop(actor, islandUuid, playerUuid), VOID);
    }

    public CompletableFuture<Void> removeCoop(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return onIsland(islandUuid, ACTION_ISLAND_COOP_REMOVE, payload, () -> islandOperator.removeCoop(actor, islandUuid, playerUuid), VOID);
    }

    public CompletableFuture<Boolean> toggleIslandLock(Actor actor, UUID islandUuid) {
        return onIsland(islandUuid, ACTION_ISLAND_LOCK_TOGGLE, islandActorPayload(actor, islandUuid), () -> islandOperator.toggleIslandLock(actor, islandUuid), resp -> resp.getBoolean("locked"));
    }

    public CompletableFuture<Boolean> toggleIslandPvp(Actor actor, UUID islandUuid) {
        return onIsland(islandUuid, ACTION_ISLAND_PVP_TOGGLE, islandActorPayload(actor, islandUuid), () -> islandOperator.toggleIslandPvp(actor, islandUuid), resp -> resp.getBoolean("pvp"));
    }

    public CompletableFuture<Void> expelPlayer(Actor actor, UUID islandUuid, UUID playerUuid) {
        JSONObject payload = islandActorPayload(actor, islandUuid);
        payload.put("playerUuid", playerUuid.toString());
        return retryingStaleRouting(() -> {
            String host = resolveHost(islandUuid);
            // Nobody stands on an unloaded island, so expelling takes no claim.
            if (host == null) {
                return CompletableFuture.failedFuture(new PlayerNotInIslandException());
            }

            return onHost(host, islandUuid, ACTION_ISLAND_EXPEL, payload, () -> islandOperator.expelPlayer(actor, islandUuid, playerUuid), VOID);
        });
    }

    // =====================================================================================
    // Internal helpers
    // =====================================================================================

    private CompletableFuture<String> ensureIslandLoaded(UUID islandUuid) {
        String host = resolveHost(islandUuid);
        if (host != null) {
            return CompletableFuture.completedFuture(host);
        }

        String targetServer = selectServer(serverRegistry.getActiveGameServers());
        if (targetServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        return loadIslandOnServer(islandUuid, targetServer);
    }

    /**
     * The target claims for itself and reports who ended up hosting: another server may
     * have won the claim first, in which case that server is returned instead.
     */
    private CompletableFuture<String> loadIslandOnServer(UUID islandUuid, String targetServer) {
        if (targetServer.equals(serverID)) {
            return islandOperator.loadIsland(islandUuid);
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        return messenger.request(targetServer, ACTION_ISLAND_LOAD, payload).thenApply(resp -> resp.getString("host"));
    }

    /**
     * Runs on the host when the island is loaded, otherwise locally under a transient claim.
     */
    private <T> CompletableFuture<T> onIsland(UUID islandUuid, String action, JSONObject payload, Supplier<CompletableFuture<T>> operation, Function<JSONObject, T> reader) {
        return retryingStaleRouting(() -> {
            String host = resolveHost(islandUuid);
            if (host != null) {
                return onHost(host, islandUuid, action, payload, operation, reader);
            }

            return underTransientClaim(islandUuid, operation, foundHost -> onHost(foundHost, islandUuid, action, payload, operation, reader));
        });
    }

    private <T> CompletableFuture<T> onHost(String host, UUID islandUuid, String action, JSONObject payload, Supplier<CompletableFuture<T>> operation, Function<JSONObject, T> reader) {
        if (host.equals(serverID)) {
            return islandOperator.asHost(islandUuid, operation);
        }

        return messenger.request(host, action, payload).thenApply(reader);
    }

    private <T> CompletableFuture<T> underTransientClaim(UUID islandUuid, Supplier<CompletableFuture<T>> operation, Function<String, CompletableFuture<T>> whenHosted) {
        String value = islandClaims.transientValue();
        return islandClaims.acquire(islandUuid, value).thenCompose(host -> {
            if (host != null) {
                return whenHosted.apply(host);
            }

            return supply(operation).handleAsync((result, error) -> {
                islandClaims.release(islandUuid, value);
                if (error != null) {
                    throw error instanceof CompletionException completion ? completion : new CompletionException(error);
                }
                return result;
            }, plugin.getBukkitAsyncExecutor());
        });
    }

    /**
     * The host released the island between our routing read and its execution: one more
     * pass against the current claim, then the failure stands.
     */
    private <T> CompletableFuture<T> retryingStaleRouting(Supplier<CompletableFuture<T>> attempt) {
        return supply(attempt).exceptionallyComposeAsync(error -> unwrap(error) instanceof IslandNotLoadedException ? supply(attempt) : CompletableFuture.failedFuture(error), plugin.getBukkitAsyncExecutor());
    }

    private String resolveHost(UUID islandUuid) {
        IslandRegistry.Host host = islandRegistry.resolveHost(islandUuid);
        if (host == null) {
            return null;
        }

        // No failover: a silent host keeps its islands until it restarts under the same name.
        if (!host.alive()) {
            throw new NoActiveServerException();
        }

        return host.server();
    }

    private JSONObject islandActorPayload(Actor actor, UUID islandUuid) {
        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put(Actor.FIELD, Actor.toJson(actor));
        return payload;
    }

    private String selectServer(Map<String, String> servers) {
        return serverSelector.selectServer(servers);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static <T> CompletableFuture<T> supply(Supplier<CompletableFuture<T>> operation) {
        try {
            return operation.get();
        } catch (Throwable error) {
            return CompletableFuture.failedFuture(error);
        }
    }
}
