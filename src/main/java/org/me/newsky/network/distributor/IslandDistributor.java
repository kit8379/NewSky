// MODIFIED FILE: IslandDistributor.java
package org.me.newsky.network.distributor;

import org.me.newsky.NewSky;
import org.me.newsky.broker.IslandBroker;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.network.lock.IslandOpLock;
import org.me.newsky.network.operator.IslandOperator;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.util.ServerUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * IslandDistributor routes island-related operations across multiple servers in a distributed setup.
 *
 * <p>Key goals:
 * <ul>
 *   <li>Choose the best server to perform an operation (via {@link ServerSelector}).</li>
 *   <li>Coordinate island loading/unloading/deleting across servers via Redis.</li>
 *   <li>Prevent race conditions using a distributed lock for sensitive operations.</li>
 *   <li>Provide reusable primitives like {@link #ensureIslandLoaded(UUID)} to avoid duplicated logic.</li>
 * </ul>
 *
 * <p>Lock policy:
 * <ul>
 *   <li>ONLY operations that can cause distributed races are locked: load / unload / delete.</li>
 *   <li>Teleport is NOT locked. However, when teleport requires a load, it uses {@link #ensureIslandLoaded(UUID)}
 *       which locks only during the load stage, then releases immediately.</li>
 * </ul>
 *
 * <p>Redis keys involved (via {@link RedisCache}):
 * <ul>
 *   <li>island_server (hash): islandUuid -> serverId</li>
 *   <li>island_op_lock:* (string): lock for sensitive island operations</li>
 * </ul>
 */
public class IslandDistributor {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final IslandOperator islandOperator;
    private final ServerSelector serverSelector;
    private final IslandOpLock islandOpLock;
    private final String serverID;

    private IslandBroker islandBroker;

    public IslandDistributor(NewSky plugin, RedisCache redisCache, IslandOperator islandOperator, ServerSelector serverSelector, IslandOpLock islandOpLock, String serverID) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.islandOperator = islandOperator;
        this.serverSelector = serverSelector;
        this.islandOpLock = islandOpLock;
        this.serverID = serverID;
    }

    public void setIslandBroker(IslandBroker islandBroker) {
        this.islandBroker = islandBroker;
    }

    // =====================================================================================
    // High-level Reusable Primitive
    // =====================================================================================

    private CompletableFuture<String> ensureIslandLoaded(UUID islandUuid) {
        // Fast-path: if already loaded, return immediately without lock.
        String already = getServerByIsland(islandUuid);
        if (already != null) {
            if (islandOpLock.isLocked(islandUuid)) {
                return CompletableFuture.failedFuture(new IslandBusyException());
            }
            return CompletableFuture.completedFuture(already);
        }

        // Not loaded: acquire lock only for the load phase.
        return withIslandOpLock(islandUuid, () -> {
            String recheck = getServerByIsland(islandUuid);
            if (recheck != null) {
                return CompletableFuture.completedFuture(recheck);
            }

            plugin.debug("IslandDistributor", "ensureIslandLoaded: island not loaded. Selecting server to load " + islandUuid);

            String targetServer = selectServer(redisCache.getActiveGameServers());
            if (targetServer == null) {
                plugin.debug("IslandDistributor", "ensureIslandLoaded: no active server available to load island " + islandUuid);
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            plugin.debug("IslandDistributor", "ensureIslandLoaded: selected server " + targetServer + " to load island " + islandUuid);

            CompletableFuture<Void> loadFuture;
            if (targetServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "ensureIslandLoaded: loading island locally on " + serverID);
                loadFuture = islandOperator.loadIsland(islandUuid);
            } else {
                plugin.debug("IslandDistributor", "ensureIslandLoaded: sending load request to remote server " + targetServer);
                loadFuture = islandBroker.sendRequest(targetServer, "load", islandUuid.toString());
            }

            return loadFuture.thenApply(v -> {
                String loadedOn = getServerByIsland(islandUuid);
                if (loadedOn == null) {
                    throw new IllegalStateException("Island load completed but island_server not set for " + islandUuid);
                }
                return loadedOn;
            });
        });
    }

    // =====================================================================================
    // Sensitive operations : load/unload/delete
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String targetServer = selectServer(redisCache.getActiveGameServers());
        if (targetServer == null) {
            plugin.debug("IslandDistributor", "createIsland: No active server available.");
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        plugin.debug("IslandDistributor", "createIsland: selected server " + targetServer + " to create island " + islandUuid);

        if (targetServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "createIsland: creating island on local server " + serverID);
            return islandOperator.createIsland(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "createIsland: sending create request to remote server " + targetServer);
            return islandBroker.sendRequest(targetServer, "create", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return withIslandOpLock(islandUuid, () -> {
            String islandServer = getServerByIsland(islandUuid);
            if (islandServer != null) {
                plugin.debug("IslandDistributor", "loadIsland: island already loaded on server " + islandServer);
                return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
            }

            plugin.debug("IslandDistributor", "loadIsland: island not loaded. Selecting server to load " + islandUuid);

            String targetServer = selectServer(redisCache.getActiveGameServers());
            if (targetServer == null) {
                plugin.debug("IslandDistributor", "loadIsland: no active server available.");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            plugin.debug("IslandDistributor", "loadIsland: selected server " + targetServer + " to load island " + islandUuid);

            if (targetServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "loadIsland: loading island locally on " + serverID);
                return islandOperator.loadIsland(islandUuid);
            } else {
                plugin.debug("IslandDistributor", "loadIsland: sending load request to remote server " + targetServer);
                return islandBroker.sendRequest(targetServer, "load", islandUuid.toString());
            }
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        return withIslandOpLock(islandUuid, () -> {
            String islandServer = getServerByIsland(islandUuid);
            if (islandServer == null) {
                plugin.debug("IslandDistributor", "unloadIsland: island not loaded anywhere, cannot unload " + islandUuid);
                return CompletableFuture.failedFuture(new IslandNotLoadedException());
            }

            plugin.debug("IslandDistributor", "unloadIsland: island loaded on server " + islandServer);

            if (islandServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "unloadIsland: unloading island locally on " + serverID);
                return islandOperator.unloadIsland(islandUuid);
            } else {
                plugin.debug("IslandDistributor", "unloadIsland: sending unload request to remote server " + islandServer);
                return islandBroker.sendRequest(islandServer, "unload", islandUuid.toString());
            }
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        return withIslandOpLock(islandUuid, () -> {
            String islandServer = getServerByIsland(islandUuid);

            if (islandServer == null) {
                plugin.debug("IslandDistributor", "deleteIsland: island not loaded anywhere, deleting locally " + islandUuid);
                return islandOperator.deleteIsland(islandUuid);
            }

            plugin.debug("IslandDistributor", "deleteIsland: island loaded on server " + islandServer);

            if (islandServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "deleteIsland: deleting locally on " + serverID);
                return islandOperator.deleteIsland(islandUuid);
            } else {
                plugin.debug("IslandDistributor", "deleteIsland: sending delete request to remote server " + islandServer);
                return islandBroker.sendRequest(islandServer, "delete", islandUuid.toString());
            }
        });
    }

    // =====================================================================================
    // Teleport operations
    // =====================================================================================

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportWorld, String teleportLocation) {
        return ensureIslandLoaded(islandUuid).thenCompose(loadedServer -> {
            if (loadedServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "teleportIsland: teleporting locally on " + serverID);
                return islandOperator.teleport(playerUuid, teleportWorld, teleportLocation);
            } else {
                plugin.debug("IslandDistributor", "teleportIsland: forwarding teleport to server " + loadedServer);
                return islandBroker.sendRequest(loadedServer, "teleport", playerUuid.toString(), teleportWorld, teleportLocation).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, loadedServer));
            }
        });
    }

    public CompletableFuture<Void> teleportLobby(UUID playerUuid, List<String> lobbyServers, String lobbyWorld, String lobbyLocation) {
        String targetLobbyServer = selectServer(redisCache.getActiveServers().entrySet().stream().filter(entry -> lobbyServers.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        if (targetLobbyServer == null) {
            plugin.debug("IslandDistributor", "teleportLobby: No active lobby server available.");
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        plugin.debug("IslandDistributor", "teleportLobby: selected lobby server " + targetLobbyServer + " for player " + playerUuid);

        if (targetLobbyServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "teleportLobby: teleporting locally on " + serverID);
            return islandOperator.teleport(playerUuid, lobbyWorld, lobbyLocation);
        } else {
            plugin.debug("IslandDistributor", "teleportLobby: forwarding teleport to server " + targetLobbyServer);
            return islandBroker.sendRequest(targetLobbyServer, "teleport", playerUuid.toString(), lobbyWorld, lobbyLocation).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, targetLobbyServer));
        }
    }

    // =====================================================================================
    // Other operations
    // =====================================================================================

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "lockIsland: island not loaded on any server.");
            return CompletableFuture.completedFuture(null);
        }

        plugin.debug("IslandDistributor", "lockIsland: island loaded on server " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "lockIsland: locking island locally on " + serverID);
            return islandOperator.lockIsland(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "lockIsland: sending lock request to remote server " + islandServer);
            return islandBroker.sendRequest(islandServer, "lock", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "expelPlayer: island not loaded on any server.");
            return CompletableFuture.completedFuture(null);
        }

        plugin.debug("IslandDistributor", "expelPlayer: island loaded on server " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "expelPlayer: expelling locally on " + serverID);
            return islandOperator.expelPlayer(islandUuid, playerUuid);
        } else {
            plugin.debug("IslandDistributor", "expelPlayer: sending expel request to remote server " + islandServer);
            return islandBroker.sendRequest(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
        }
    }

    public void updateIslandBorder(UUID islandUuid, int size) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "updateIslandBorder: island not loaded on any server.");
            return;
        }

        plugin.debug("IslandDistributor", "updateIslandBorder: island loaded on server " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "updateIslandBorder: updating border locally on " + serverID);
            islandOperator.updateIslandBorder(islandUuid, size);
        } else {
            plugin.debug("IslandDistributor", "updateIslandBorder: sending update border request to remote server " + islandServer);
            islandBroker.sendRequest(islandServer, "update_border", islandUuid.toString(), String.valueOf(size));
        }
    }

    // =====================================================================================
    // Internal helpers
    // =====================================================================================

    private <T> CompletableFuture<T> withIslandOpLock(UUID islandUuid, Supplier<CompletableFuture<T>> action) {
        return islandOpLock.withLock(islandUuid, action);
    }

    private String selectServer(Map<String, String> servers) {
        return serverSelector.selectServer(servers);
    }

    private String getServerByIsland(UUID islandUuid) {
        return redisCache.getIslandLoadedServer(islandUuid).orElse(null);
    }
}