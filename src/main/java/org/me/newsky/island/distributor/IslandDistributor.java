package org.me.newsky.island.distributor;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.broker.IslandBroker;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.island.operation.IslandOperation;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.util.ComponentUtils;
import org.me.newsky.util.ServerUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class IslandDistributor {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final IslandOperation islandOperation;
    private final ServerSelector serverSelector;
    private final String serverID;

    private IslandBroker islandBroker;

    public IslandDistributor(NewSky plugin, RedisCache redisCache, IslandOperation islandOperation, ServerSelector serverSelector, String serverID) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.islandOperation = islandOperation;
        this.serverSelector = serverSelector;
        this.serverID = serverID;
    }

    public void setIslandBroker(IslandBroker islandBroker) {
        this.islandBroker = islandBroker;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String targetServer = selectServer(redisCache.getActiveGameServers());

        if (targetServer == null) {
            plugin.debug("IslandDistributor", "No active server available for island creation.");
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        plugin.debug("IslandDistributor", "Selected server " + targetServer + " to create island " + islandUuid);

        if (targetServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Creating island on local server: " + serverID);
            return islandOperation.createIsland(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "Sending create request to remote server: " + targetServer);
            return islandBroker.sendRequest(targetServer, "create", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server. Deleting locally.");
            return islandOperation.deleteIsland(islandUuid);
        }

        plugin.debug("IslandDistributor", "Island loaded on server: " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Deleting island on local server: " + serverID);
            return islandOperation.deleteIsland(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "Sending delete request to remote server: " + islandServer);
            return islandBroker.sendRequest(islandServer, "delete", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server. Selecting server to load the island.");

            String targetServer = selectServer(redisCache.getActiveGameServers());

            if (targetServer == null) {
                plugin.debug("IslandDistributor", "No active server available to load the island.");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            plugin.debug("IslandDistributor", "Selected server " + targetServer + " to load island " + islandUuid);

            if (targetServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Loading island on local server: " + serverID);
                return islandOperation.loadIsland(islandUuid);
            } else {
                plugin.debug("IslandDistributor", "Sending load request to remote server: " + targetServer);
                return islandBroker.sendRequest(targetServer, "load", islandUuid.toString());
            }
        } else {
            plugin.debug("IslandDistributor", "Island already loaded on server: " + islandServer);
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server. Cannot unload.");
            return CompletableFuture.failedFuture(new IslandNotLoadedException());
        }

        plugin.debug("IslandDistributor", "Island loaded on server: " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Unloading island on local server: " + serverID);
            return islandOperation.unloadIsland(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "Sending unload request to remote server: " + islandServer);
            return islandBroker.sendRequest(islandServer, "unload", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportWorld, String teleportLocation) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server. Selecting server to load the island.");

            String targetServer = selectServer(redisCache.getActiveGameServers());

            if (targetServer == null) {
                plugin.debug("IslandDistributor", "No active server available to load the island.");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            plugin.debug("IslandDistributor", "Selected server " + targetServer + " to load island " + islandUuid);

            if (targetServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Loading island on local server: " + serverID);
                return islandOperation.loadIsland(islandUuid).thenCompose(v -> {
                    plugin.debug("IslandDistributor", "Teleporting player on local server: " + serverID);
                    return islandOperation.teleport(playerUuid, teleportWorld, teleportLocation);
                });
            } else {
                plugin.debug("IslandDistributor", "Sending load request to remote server: " + targetServer);
                return islandBroker.sendRequest(targetServer, "load", islandUuid.toString()).thenCompose(v -> {
                    plugin.debug("IslandDistributor", "Sending teleport request to remote server: " + targetServer);
                    return islandBroker.sendRequest(targetServer, "teleport", playerUuid.toString(), teleportWorld, teleportLocation);
                }).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, targetServer));
            }
        } else {
            if (islandServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Teleporting player on local server: " + serverID);
                return islandOperation.teleport(playerUuid, teleportWorld, teleportLocation);
            } else {
                plugin.debug("IslandDistributor", "Sending teleport request to remote server: " + islandServer);
                return islandBroker.sendRequest(islandServer, "teleport", playerUuid.toString(), teleportWorld, teleportLocation).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, islandServer));
            }
        }
    }

    public CompletableFuture<Void> teleportLobby(UUID playerUuid, List<String> lobbyServers, String lobbyWorld, String lobbyLocation) {
        String targetLobbyServer = selectServer(redisCache.getActiveServers().entrySet().stream().filter(entry -> lobbyServers.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        if (targetLobbyServer == null) {
            plugin.debug("IslandDistributor", "No active lobby server available.");
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        plugin.debug("IslandDistributor", "Selected active lobby server: " + targetLobbyServer + " for player: " + playerUuid);

        if (targetLobbyServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Teleporting player to local lobby world: " + lobbyWorld);
            return islandOperation.teleport(playerUuid, lobbyWorld, lobbyLocation);
        } else {
            plugin.debug("IslandDistributor", "Sending lobby teleport request to remote server: " + targetLobbyServer);
            return islandBroker.sendRequest(targetLobbyServer, "teleport", playerUuid.toString(), lobbyWorld, lobbyLocation).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, targetLobbyServer));
        }
    }

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server.");
            return CompletableFuture.completedFuture(null);
        }

        plugin.debug("IslandDistributor", "Island loaded on server: " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Locking island on local server: " + serverID);
            return islandOperation.lockIsland(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "Sending lock request to remote server: " + islandServer);
            return islandBroker.sendRequest(islandServer, "lock", islandUuid.toString());
        }
    }


    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server.");
            return CompletableFuture.completedFuture(null);
        }

        plugin.debug("IslandDistributor", "Island loaded on server: " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Expelling player on local server: " + serverID);
            return islandOperation.expelPlayer(islandUuid, playerUuid);
        } else {
            plugin.debug("IslandDistributor", "Sending expel request to remote server: " + islandServer);
            return islandBroker.sendRequest(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
        }
    }

    public CompletableFuture<Void> sendMessage(UUID playerUuid, Component message) {
        String playerServer = getServerByPlayer(playerUuid);

        if (playerServer == null) {
            plugin.debug("IslandDistributor", "Player is not currently online on any server");
            return CompletableFuture.completedFuture(null);
        }

        plugin.debug("IslandDistributor", "Player " + playerUuid + " is on server: " + playerServer);

        if (playerServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Sending message to player on current server " + serverID);
            return islandOperation.sendMessage(playerUuid, message);
        } else {
            plugin.debug("IslandDistributor", "Forwarding message to player on server " + playerServer);
            return islandBroker.sendRequest(playerServer, "message", playerUuid.toString(), ComponentUtils.serialize(message));
        }
    }


    private String selectServer(Map<String, String> servers) {
        return serverSelector.selectServer(servers);
    }

    private String getServerByIsland(UUID islandUuid) {
        return redisCache.getIslandLoadedServer(islandUuid).orElse(null);
    }

    private String getServerByPlayer(UUID playerUuid) {
        return redisCache.getPlayerOnlineServer(playerUuid).orElse(null);
    }
}
