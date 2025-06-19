package org.me.newsky.island.distributor;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.broker.IslandBroker;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.island.operation.IslandOperation;
import org.me.newsky.redis.RedisCache;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.util.ComponentUtils;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
                    return islandBroker.sendRequest(targetServer, "teleport", playerUuid.toString(), teleportWorld, teleportLocation).thenRun(() -> connectToServer(playerUuid, targetServer));
                });
            }
        } else {
            if (islandServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Teleporting player on local server: " + serverID);
                return islandOperation.teleport(playerUuid, teleportWorld, teleportLocation);
            } else {
                plugin.debug("IslandDistributor", "Sending teleport request to remote server: " + islandServer);
                return islandBroker.sendRequest(islandServer, "teleport", playerUuid.toString(), teleportWorld, teleportLocation).thenRun(() -> connectToServer(playerUuid, islandServer));
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
            return islandBroker.sendRequest(targetLobbyServer, "teleport", playerUuid.toString(), lobbyWorld, lobbyLocation).thenRun(() -> connectToServer(playerUuid, targetLobbyServer));
        }
    }


    public void lockIsland(UUID islandUuid) {
        plugin.debug("IslandDistributor", "Broadcasting lock request for island: " + islandUuid);
        islandBroker.sendBroadcast("lock", islandUuid.toString());
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        plugin.debug("IslandDistributor", "Broadcasting expel request for island: " + islandUuid + ", player: " + playerUuid);
        islandBroker.sendBroadcast("expel", islandUuid.toString(), playerUuid.toString());
    }

    public void sendMessage(UUID playerUuid, Component message) {
        plugin.debug("IslandDistributor", "Broadcasting message to player: " + playerUuid + ", Message: " + ComponentUtils.serialize(message));
        islandBroker.sendBroadcast("message", playerUuid.toString(), ComponentUtils.serialize(message));
    }

    private String selectServer(Map<String, String> servers) {
        return serverSelector.selectServer(servers);
    }

    private String getServerByIsland(UUID islandUuid) {
        return redisCache.getIslandLoadedServer(islandUuid).orElse(null);
    }

    private void connectToServer(UUID playerUuid, String serverName) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            try (ByteArrayOutputStream byteArray = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(byteArray)) {
                out.writeUTF("Connect");
                out.writeUTF(serverName);
                player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
                plugin.debug("IslandDistributor", "Sent plugin message to player " + player.getName() + " to connect to server: " + serverName);
            } catch (IOException e) {
                plugin.severe("Failed to send plugin message to player " + player.getName(), e);
            }
        }
    }
}
