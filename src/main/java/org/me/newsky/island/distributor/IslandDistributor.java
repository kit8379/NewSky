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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid) {
        plugin.debug("IslandDistributor", "Creating island with UUID: " + islandUuid + ", Owner UUID: " + ownerUuid);

        String targetServer = selectServer();

        if (targetServer == null) {
            plugin.debug("IslandDistributor", "No active server available for island creation.");
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        plugin.debug("IslandDistributor", "Selected server for island creation: " + targetServer);

        if (targetServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Creating island on local server: " + serverID);
            return islandOperation.createIsland(islandUuid, ownerUuid);
        } else {
            plugin.debug("IslandDistributor", "Forwarding island creation request to server: " + targetServer);
            return islandBroker.sendRequest(targetServer, "create", islandUuid.toString(), ownerUuid.toString());
        }
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        plugin.debug("IslandDistributor", "Deleting island with UUID: " + islandUuid);

        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not found on any server, deleting locally.");
            return islandOperation.deleteIsland(islandUuid);
        }

        plugin.debug("IslandDistributor", "Island found on server: " + islandServer + ", forwarding delete request.");

        if (islandServer.equals(serverID)) {
            plugin.debug("IslandDistributor", "Deleting island on local server: " + serverID);
            return islandOperation.deleteIsland(islandUuid);
        } else {
            plugin.debug("IslandDistributor", "Forwarding delete request to server: " + islandServer);
            return islandBroker.sendRequest(islandServer, "delete", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        plugin.debug("IslandDistributor", "Loading island with UUID: " + islandUuid);

        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server, selecting a server to load.");

            String targetServer = selectServer();

            if (targetServer == null) {
                plugin.debug("IslandDistributor", "No active server available for loading island.");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            } else {
                plugin.debug("IslandDistributor", "Selected server for loading island: " + targetServer);
                if (targetServer.equals(serverID)) {
                    plugin.debug("IslandDistributor", "Loading island on local server: " + serverID);
                    return islandOperation.loadIsland(islandUuid);
                } else {
                    plugin.debug("IslandDistributor", "Forwarding load request to server: " + targetServer);
                    return islandBroker.sendRequest(targetServer, "load", islandUuid.toString());
                }
            }
        } else {
            plugin.debug("IslandDistributor", "Island already loaded on server: " + islandServer);
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        plugin.debug("IslandDistributor", "Unloading island with UUID: " + islandUuid);

        String islandServer = getServerByIsland(islandUuid);


        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server, cannot unload.");
            return CompletableFuture.failedFuture(new IslandNotLoadedException());
        } else {
            plugin.debug("IslandDistributor", "Island found on server: " + islandServer + ", forwarding unload request.");
            if (islandServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Unloading island on local server: " + serverID);
                return islandOperation.unloadIsland(islandUuid);
            } else {
                plugin.debug("IslandDistributor", "Forwarding unload request to server: " + islandServer);
                return islandBroker.sendRequest(islandServer, "unload", islandUuid.toString());
            }
        }
    }

    public void lockIsland(UUID islandUuid) {
        plugin.debug("IslandDistributor", "Locking island with UUID: " + islandUuid);

        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server, locking locally.");
            CompletableFuture.completedFuture(null);
        } else {
            plugin.debug("IslandDistributor", "Island found on server: " + islandServer + ", forwarding lock request.");
            if (islandServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Locking island on local server: " + serverID);
                islandOperation.lockIsland(islandUuid);
            } else {
                plugin.debug("IslandDistributor", "Forwarding lock request to server: " + islandServer);
                islandBroker.sendRequest(islandServer, "lock", islandUuid.toString());
            }
        }
    }

    public CompletableFuture<Void> teleportIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        plugin.debug("IslandDistributor", "Teleporting player with UUID: " + playerUuid + " to island with UUID: " + islandUuid + " at location: " + teleportLocation);

        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server, selecting a server for teleportation.");

            String targetServer = selectServer();

            if (targetServer == null) {
                plugin.debug("IslandDistributor", "No active server available for teleportation.");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            plugin.debug("IslandDistributor", "Selected server for teleportation: " + targetServer);

            if (targetServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Teleporting island on local server: " + serverID);
                return islandOperation.teleportIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                plugin.debug("IslandDistributor", "Forwarding teleport request to server: " + targetServer);
                return islandBroker.sendRequest(targetServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, targetServer));
            }
        } else {
            plugin.debug("IslandDistributor", "Island found on server: " + islandServer + ", forwarding teleport request.");
            if (islandServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Teleporting island on local server: " + serverID);
                return islandOperation.teleportIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                plugin.debug("IslandDistributor", "Forwarding teleport request to server: " + islandServer);
                return islandBroker.sendRequest(islandServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, islandServer));
            }
        }
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        plugin.debug("IslandDistributor", "Expelling player with UUID: " + playerUuid + " from island with UUID: " + islandUuid);

        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug("IslandDistributor", "Island not loaded on any server, expelling player locally.");
            CompletableFuture.completedFuture(null);
        } else {
            plugin.debug("IslandDistributor", "Island found on server: " + islandServer + ", forwarding expel request.");
            if (islandServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Expelling player on local server: " + serverID);
                islandOperation.expelPlayer(islandUuid, playerUuid);
            } else {
                plugin.debug("IslandDistributor", "Forwarding expel request to server: " + islandServer);
                islandBroker.sendRequest(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
            }
        }
    }

    public void sendPlayerMessage(UUID playerUuid, Component message) {
        plugin.debug("IslandDistributor", "Sending message to player with UUID: " + playerUuid + ", Message: " + ComponentUtils.serialize(message));

        String playerName = Bukkit.getOfflinePlayer(playerUuid).getName();
        String playerServer = getServerByPlayer(playerName);

        if (playerServer == null) {
            plugin.debug("IslandDistributor", "Player not online on any server.");
        } else {
            plugin.debug("IslandDistributor", "Player found on server: " + playerServer + ", sending message.");
            if (playerServer.equals(serverID)) {
                plugin.debug("IslandDistributor", "Sending message to player on local server: " + serverID);
                islandOperation.sendPlayerMessage(playerUuid, message);
            } else {
                plugin.debug("IslandDistributor", "Forwarding message to player on server: " + playerServer);
                islandBroker.sendRequest(playerServer, "message", playerUuid.toString(), ComponentUtils.serialize(message));
            }
        }
    }

    private String selectServer() {
        Map<String, String> activeServers = redisCache.getActiveGameServers();
        return serverSelector.selectServer(activeServers);
    }

    private String getServerByIsland(UUID islandUuid) {
        return redisCache.getIslandLoadedServer(islandUuid).orElse(null);
    }

    public String getServerByPlayer(String playerName) {
        return redisCache.getOnlinePlayerServer(playerName).orElse(null);
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
