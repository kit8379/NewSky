package org.me.newsky.network.distributor;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.broker.Broker;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.network.operator.Operator;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.util.ComponentUtils;
import org.me.newsky.util.ServerUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Distributor {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final Operator operator;
    private final ServerSelector serverSelector;
    private final String serverID;

    private Broker broker;

    public Distributor(NewSky plugin, RedisCache redisCache, Operator operator, ServerSelector serverSelector, String serverID) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.operator = operator;
        this.serverSelector = serverSelector;
        this.serverID = serverID;
    }

    public void setIslandBroker(Broker broker) {
        this.broker = broker;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String targetServer = selectServer(redisCache.getActiveGameServers());

        if (targetServer == null) {
            plugin.debug("Distributor", "No active server available for island creation.");
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        plugin.debug("Distributor", "Selected server " + targetServer + " to create island " + islandUuid);

        if (targetServer.equals(serverID)) {
            plugin.debug("Distributor", "Creating island on local server: " + serverID);
            return operator.createIsland(islandUuid);
        } else {
            plugin.debug("Distributor", "Sending create request to remote server: " + targetServer);
            return broker.sendRequest(targetServer, "create", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("Distributor", "Island not loaded on any server. Deleting locally.");
            return operator.deleteIsland(islandUuid);
        }

        plugin.debug("Distributor", "Island loaded on server: " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("Distributor", "Deleting island on local server: " + serverID);
            return operator.deleteIsland(islandUuid);
        } else {
            plugin.debug("Distributor", "Sending delete request to remote server: " + islandServer);
            return broker.sendRequest(islandServer, "delete", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("Distributor", "Island not loaded on any server. Selecting server to load the island.");

            String targetServer = selectServer(redisCache.getActiveGameServers());

            if (targetServer == null) {
                plugin.debug("Distributor", "No active server available to load the island.");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            plugin.debug("Distributor", "Selected server " + targetServer + " to load island " + islandUuid);

            if (targetServer.equals(serverID)) {
                plugin.debug("Distributor", "Loading island on local server: " + serverID);
                return operator.loadIsland(islandUuid);
            } else {
                plugin.debug("Distributor", "Sending load request to remote server: " + targetServer);
                return broker.sendRequest(targetServer, "load", islandUuid.toString());
            }
        } else {
            plugin.debug("Distributor", "Island already loaded on server: " + islandServer);
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("Distributor", "Island not loaded on any server. Cannot unload.");
            return CompletableFuture.failedFuture(new IslandNotLoadedException());
        }

        plugin.debug("Distributor", "Island loaded on server: " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("Distributor", "Unloading island on local server: " + serverID);
            return operator.unloadIsland(islandUuid);
        } else {
            plugin.debug("Distributor", "Sending unload request to remote server: " + islandServer);
            return broker.sendRequest(islandServer, "unload", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportWorld, String teleportLocation) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("Distributor", "Island not loaded on any server. Selecting server to load the island.");

            String targetServer = selectServer(redisCache.getActiveGameServers());

            if (targetServer == null) {
                plugin.debug("Distributor", "No active server available to load the island.");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            plugin.debug("Distributor", "Selected server " + targetServer + " to load island " + islandUuid);

            if (targetServer.equals(serverID)) {
                plugin.debug("Distributor", "Loading island on local server: " + serverID);
                return operator.loadIsland(islandUuid).thenCompose(v -> {
                    plugin.debug("Distributor", "Teleporting player on local server: " + serverID);
                    return operator.teleport(playerUuid, teleportWorld, teleportLocation);
                });
            } else {
                plugin.debug("Distributor", "Sending load request to remote server: " + targetServer);
                return broker.sendRequest(targetServer, "load", islandUuid.toString()).thenCompose(v -> {
                    plugin.debug("Distributor", "Sending teleport request to remote server: " + targetServer);
                    return broker.sendRequest(targetServer, "teleport", playerUuid.toString(), teleportWorld, teleportLocation);
                }).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, targetServer));
            }
        } else {
            if (islandServer.equals(serverID)) {
                plugin.debug("Distributor", "Teleporting player on local server: " + serverID);
                return operator.teleport(playerUuid, teleportWorld, teleportLocation);
            } else {
                plugin.debug("Distributor", "Sending teleport request to remote server: " + islandServer);
                return broker.sendRequest(islandServer, "teleport", playerUuid.toString(), teleportWorld, teleportLocation).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, islandServer));
            }
        }
    }

    public CompletableFuture<Void> teleportLobby(UUID playerUuid, List<String> lobbyServers, String lobbyWorld, String lobbyLocation) {
        String targetLobbyServer = selectServer(redisCache.getActiveServers().entrySet().stream().filter(entry -> lobbyServers.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        if (targetLobbyServer == null) {
            plugin.debug("Distributor", "No active lobby server available.");
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        plugin.debug("Distributor", "Selected active lobby server: " + targetLobbyServer + " for player: " + playerUuid);

        if (targetLobbyServer.equals(serverID)) {
            plugin.debug("Distributor", "Teleporting player to local lobby world: " + lobbyWorld);
            return operator.teleport(playerUuid, lobbyWorld, lobbyLocation);
        } else {
            plugin.debug("Distributor", "Sending lobby teleport request to remote server: " + targetLobbyServer);
            return broker.sendRequest(targetLobbyServer, "teleport", playerUuid.toString(), lobbyWorld, lobbyLocation).thenCompose(v -> ServerUtil.connectToServer(plugin, playerUuid, targetLobbyServer));
        }
    }

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("Distributor", "Island not loaded on any server.");
            return CompletableFuture.completedFuture(null);
        }

        plugin.debug("Distributor", "Island loaded on server: " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("Distributor", "Locking island on local server: " + serverID);
            return operator.lockIsland(islandUuid);
        } else {
            plugin.debug("Distributor", "Sending lock request to remote server: " + islandServer);
            return broker.sendRequest(islandServer, "lock", islandUuid.toString());
        }
    }


    public CompletableFuture<Void> expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("Distributor", "Island not loaded on any server.");
            return CompletableFuture.completedFuture(null);
        }

        plugin.debug("Distributor", "Island loaded on server: " + islandServer);

        if (islandServer.equals(serverID)) {
            plugin.debug("Distributor", "Expelling player on local server: " + serverID);
            return operator.expelPlayer(islandUuid, playerUuid);
        } else {
            plugin.debug("Distributor", "Sending expel request to remote server: " + islandServer);
            return broker.sendRequest(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
        }
    }

    public CompletableFuture<Void> sendMessage(UUID playerUuid, Component message) {
        String playerServer = getServerByPlayer(playerUuid);

        if (playerServer == null) {
            plugin.debug("Distributor", "Player is not currently online on any server");
            return CompletableFuture.completedFuture(null);
        }

        plugin.debug("Distributor", "Player " + playerUuid + " is on server: " + playerServer);

        if (playerServer.equals(serverID)) {
            plugin.debug("Distributor", "Sending message to player on current server " + serverID);
            return operator.sendMessage(playerUuid, message);
        } else {
            plugin.debug("Distributor", "Forwarding message to player on server " + playerServer);
            return broker.sendRequest(playerServer, "message", playerUuid.toString(), ComponentUtils.serialize(message));
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
