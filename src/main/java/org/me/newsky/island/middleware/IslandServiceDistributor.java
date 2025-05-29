package org.me.newsky.island.middleware;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.island.operation.LocalIslandOperation;
import org.me.newsky.network.Broker;
import org.me.newsky.routing.ServerSelector;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class IslandServiceDistributor {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final Broker broker;
    private final LocalIslandOperation localIslandOperation;
    private final ServerSelector serverSelector;
    private final String serverID;

    public IslandServiceDistributor(NewSky plugin, CacheHandler cacheHandler, Broker broker, LocalIslandOperation localIslandOperation, ServerSelector serverSelector, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.broker = broker;
        this.localIslandOperation = localIslandOperation;
        this.serverSelector = serverSelector;
        this.serverID = serverID;
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID ownerUuid, String spawnLocation) {
        String targetServer = selectServer();

        if (targetServer == null) {
            plugin.debug(getClass().getSimpleName(), "No active server found to handle island creation");
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        if (targetServer.equals(serverID)) {
            plugin.debug(getClass().getSimpleName(), "Creating island in current server " + serverID);
            return localIslandOperation.createIsland(islandUuid, ownerUuid, spawnLocation);
        } else {
            plugin.debug(getClass().getSimpleName(), "Forwarding island creation request to server " + targetServer);
            return broker.sendRequest(targetServer, "create", islandUuid.toString(), ownerUuid.toString(), spawnLocation);
        }
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug(getClass().getSimpleName(), "Island " + islandUuid + " is not currently loaded on any server");
            String targetServer = selectServer();
            if (targetServer == null) {
                plugin.debug(getClass().getSimpleName(), "No active server available for deletion");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            if (targetServer.equals(serverID)) {
                plugin.debug(getClass().getSimpleName(), "Deleting island in current server " + serverID);
                return localIslandOperation.deleteIsland(islandUuid);
            } else {
                plugin.debug(getClass().getSimpleName(), "Forwarding deletion request to server " + targetServer);
                return broker.sendRequest(targetServer, "delete", islandUuid.toString());
            }
        } else {
            plugin.debug(getClass().getSimpleName(), "Island " + islandUuid + " is currently on server: " + islandServer);
            if (islandServer.equals(serverID)) {
                plugin.debug(getClass().getSimpleName(), "Deleting island in current server " + serverID);
                return localIslandOperation.deleteIsland(islandUuid);
            } else {
                plugin.debug(getClass().getSimpleName(), "Forwarding deletion request to server " + islandServer);
                return broker.sendRequest(islandServer, "delete", islandUuid.toString());
            }
        }
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug(getClass().getSimpleName(), "Island " + islandUuid + " is not currently loaded on any server");
            String targetServer = selectServer();
            if (targetServer == null) {
                plugin.debug(getClass().getSimpleName(), "No active server available to load island");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            if (targetServer.equals(serverID)) {
                plugin.debug(getClass().getSimpleName(), "Loading island in current server " + serverID);
                return localIslandOperation.loadIsland(islandUuid);
            } else {
                plugin.debug(getClass().getSimpleName(), "Forwarding load request to server " + targetServer);
                return broker.sendRequest(targetServer, "load", islandUuid.toString());
            }
        } else {
            plugin.debug(getClass().getSimpleName(), "Island " + islandUuid + " is already loaded on server: " + islandServer);
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug(getClass().getSimpleName(), "Island " + islandUuid + " is not currently loaded on any server");
            return CompletableFuture.failedFuture(new IslandNotLoadedException());
        }

        if (islandServer.equals(serverID)) {
            plugin.debug(getClass().getSimpleName(), "Unloading island in current server " + serverID);
            return localIslandOperation.unloadIsland(islandUuid);
        } else {
            plugin.debug(getClass().getSimpleName(), "Forwarding unload request to server " + islandServer);
            return broker.sendRequest(islandServer, "unload", islandUuid.toString());
        }
    }

    public void lockIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug(getClass().getSimpleName(), "Island " + islandUuid + " is not currently loaded on any server");
            CompletableFuture.completedFuture(null);
        } else {
            if (islandServer.equals(serverID)) {
                plugin.debug(getClass().getSimpleName(), "Locking island in current server " + serverID);
                localIslandOperation.lockIsland(islandUuid);
            } else {
                plugin.debug(getClass().getSimpleName(), "Forwarding lock request to server " + islandServer);
                broker.sendRequest(islandServer, "lock", islandUuid.toString());
            }
        }
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug(getClass().getSimpleName(), "Island " + islandUuid + " is not currently loaded on any server");
            CompletableFuture.completedFuture(null);
        } else {
            if (islandServer.equals(serverID)) {
                plugin.debug(getClass().getSimpleName(), "Expelling player in current server " + serverID);
                localIslandOperation.expelPlayer(islandUuid, playerUuid);
            } else {
                plugin.debug(getClass().getSimpleName(), "Forwarding expel request to server " + islandServer);
                broker.sendRequest(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
            }
        }
    }

    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        String islandServer = getServerByIsland(islandUuid);

        if (islandServer == null) {
            plugin.debug(getClass().getSimpleName(), "Island " + islandUuid + " is not currently loaded on any server");
            String targetServer = selectServer();
            if (targetServer == null) {
                plugin.debug(getClass().getSimpleName(), "No active server available for teleportation");
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }

            if (targetServer.equals(serverID)) {
                plugin.debug(getClass().getSimpleName(), "Teleporting in current server " + serverID);
                return localIslandOperation.teleportToIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                plugin.debug(getClass().getSimpleName(), "Forwarding teleport request to server " + targetServer);
                return broker.sendRequest(targetServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, targetServer));
            }
        } else {
            if (islandServer.equals(serverID)) {
                plugin.debug(getClass().getSimpleName(), "Teleporting in current server " + serverID);
                return localIslandOperation.teleportToIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                plugin.debug(getClass().getSimpleName(), "Forwarding teleport request to server " + islandServer);
                return broker.sendRequest(islandServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, islandServer));
            }
        }
    }

    private String selectServer() {
        Map<String, String> activeServers = cacheHandler.getActiveServers();
        return serverSelector.selectServer(activeServers);
    }

    private String getServerByIsland(UUID islandUuid) {
        return cacheHandler.getIslandLoadedServer(islandUuid);
    }

    private void connectToServer(UUID playerUuid, String serverName) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            try (ByteArrayOutputStream byteArray = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(byteArray)) {
                out.writeUTF("Connect");
                out.writeUTF(serverName);
                player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
                plugin.debug(getClass().getSimpleName(), "Sent plugin message to connect player " + player.getName() + " to server " + serverName);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send plugin message to player " + player.getName(), e);
            }
        }
    }
}
