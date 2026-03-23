package org.me.newsky.network;

import org.me.newsky.NewSky;
import org.me.newsky.broker.IslandBroker;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.IslandOperationBusyException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.lock.IslandOperationLock;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.state.IslandServerState;
import org.me.newsky.state.ServerHeartbeatState;
import org.me.newsky.util.ServerUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IslandDistributor {

    private final NewSky plugin;
    private final IslandOperator islandOperator;
    private final IslandOperationLock islandOperationLock;
    private final ServerSelector serverSelector;
    private final ServerHeartbeatState serverHeartbeatState;
    private final IslandServerState islandServerState;
    private final String serverID;

    private IslandBroker islandBroker;

    public IslandDistributor(NewSky plugin, IslandOperator islandOperator, IslandOperationLock islandOperationLock, ServerSelector serverSelector, ServerHeartbeatState serverHeartbeatState, IslandServerState islandServerState, String serverID) {
        this.plugin = plugin;
        this.islandOperator = islandOperator;
        this.islandOperationLock = islandOperationLock;
        this.serverSelector = serverSelector;
        this.serverHeartbeatState = serverHeartbeatState;
        this.islandServerState = islandServerState;
        this.serverID = serverID;
    }

    public void setIslandBroker(IslandBroker islandBroker) {
        this.islandBroker = islandBroker;
    }

    // =====================================================================================
    // High-level Reusable Primitive
    // =====================================================================================

    private CompletableFuture<String> ensureIslandLoaded(UUID islandUuid) {
        String alreadyLoadedServer = getServerByIsland(islandUuid);
        if (alreadyLoadedServer != null) {
            if (islandOperationLock.isLocked(islandUuid)) {
                return CompletableFuture.failedFuture(new IslandOperationBusyException());
            }
            return CompletableFuture.completedFuture(alreadyLoadedServer);
        }

        return withIslandOpLock(islandUuid, () -> {
            String recheckedServer = getServerByIsland(islandUuid);
            if (recheckedServer != null) {
                return CompletableFuture.completedFuture(recheckedServer);
            }

            plugin.debug("IslandDistributor", "ensureIslandLoaded: island not loaded. Selecting server to load " + islandUuid);

            String targetServer = selectServer(serverHeartbeatState.getActiveGameServers());
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
    // Sensitive operations : create/load/unload/delete
    // =====================================================================================

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        return withIslandOpLock(islandUuid, () -> {
            String targetServer = selectServer(serverHeartbeatState.getActiveGameServers());
            if (targetServer == null) {
                plugin.debug("IslandDistributor", "createIsland: no active server available.");
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
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        return withIslandOpLock(islandUuid, () -> {
            String islandServer = getServerByIsland(islandUuid);
            if (islandServer != null) {
                plugin.debug("IslandDistributor", "loadIsland: island already loaded on server " + islandServer);
                return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
            }

            plugin.debug("IslandDistributor", "loadIsland: island not loaded. Selecting server to load " + islandUuid);

            String targetServer = selectServer(serverHeartbeatState.getActiveGameServers());
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
        String targetLobbyServer = selectServer(serverHeartbeatState.getActiveServers().entrySet().stream().filter(entry -> lobbyServers.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        if (targetLobbyServer == null) {
            plugin.debug("IslandDistributor", "teleportLobby: no active lobby server available.");
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

    public void lockIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "lockIsland: island not loaded on any server.");
            return;
        }

        plugin.debug("IslandDistributor", "lockIsland: island loaded on server " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "lockIsland: locking island locally on " + serverID);
            islandOperator.lockIsland(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "lockIsland: sending lock event to remote server " + islandServer);
            islandBroker.sendEvent(islandServer, "lock", islandUuid.toString());
        }
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "expelPlayer: island not loaded on any server.");
            return;
        }

        plugin.debug("IslandDistributor", "expelPlayer: island loaded on server " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "expelPlayer: expelling locally on " + serverID);
            islandOperator.expelPlayer(islandUuid, playerUuid);
        } else {
            plugin.debug("IslandDistributor", "expelPlayer: sending expel event to remote server " + islandServer);
            islandBroker.sendEvent(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
        }
    }

    public void updateBorder(UUID islandUuid, int size) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "updateBorder: island not loaded on any server.");
            return;
        }

        plugin.debug("IslandDistributor", "updateBorder: island loaded on server " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "updateBorder: updating border locally on " + serverID);
            islandOperator.updateBorder(islandUuid, size);
        } else {
            plugin.debug("IslandDistributor", "updateBorder: sending update border event to remote server " + islandServer);
            islandBroker.sendEvent(islandServer, "update_border", islandUuid.toString(), String.valueOf(size));
        }
    }

    public void reloadSnapshot(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "reloadSnapshot: island not loaded on any server.");
            return;
        }

        plugin.debug("IslandDistributor", "reloadSnapshot: island loaded on server " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "reloadSnapshot: reloading snapshot locally on " + serverID);
            islandOperator.reloadSnapshot(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "reloadSnapshot: sending reload snapshot event to remote server " + islandServer);
            islandBroker.sendEvent(islandServer, "reload_snapshot", islandUuid.toString());
        }
    }

    // =====================================================================================
    // Internal helpers
    // =====================================================================================

    private <T> CompletableFuture<T> withIslandOpLock(UUID islandUuid, Supplier<CompletableFuture<T>> action) {
        return islandOperationLock.withLock(islandUuid, action);
    }

    private String selectServer(Map<String, String> servers) {
        return serverSelector.selectServer(servers);
    }

    private String getServerByIsland(UUID islandUuid) {
        return islandServerState.getIslandLoadedServer(islandUuid).orElse(null);
    }
}