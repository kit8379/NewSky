package org.me.newsky.island.middleware;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.network.Broker;
import org.me.newsky.routing.ServerSelector;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Handles pre-island operations such as creating, deleting, loading, unloading, and locking islands.
 * This class is responsible for sending requests to the appropriate server to perform these operations.
 */
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

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String targetServer = selectServer();
        if (targetServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }
        if (targetServer.equals(serverID)) {
            return localIslandOperation.createIsland(islandUuid);
        } else {
            return broker.sendRequest(targetServer, "create", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            String targetServer = selectServer();
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }
            if (targetServer.equals(serverID)) {
                return localIslandOperation.deleteIsland(islandUuid);
            } else {
                return broker.sendRequest(targetServer, "delete", islandUuid.toString());
            }
        } else if (islandServer.equals(serverID)) {
            return localIslandOperation.deleteIsland(islandUuid);
        } else {
            return broker.sendRequest(islandServer, "delete", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            String targetServer = selectServer();
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }
            if (targetServer.equals(serverID)) {
                return localIslandOperation.loadIsland(islandUuid);
            } else {
                return broker.sendRequest(targetServer, "load", islandUuid.toString());
            }
        } else {
            return CompletableFuture.failedFuture(new IslandAlreadyLoadedException());
        }
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            return CompletableFuture.failedFuture(new IslandNotLoadedException());
        } else {
            if (islandServer.equals(serverID)) {
                return localIslandOperation.unloadIsland(islandUuid);
            } else {
                return broker.sendRequest(islandServer, "unload", islandUuid.toString());
            }
        }
    }

    public void lockIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            CompletableFuture.completedFuture(null);
        } else {
            if (islandServer.equals(serverID)) {
                localIslandOperation.lockIsland(islandUuid);
            } else {
                broker.sendRequest(islandServer, "lock", islandUuid.toString());
            }
        }
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            CompletableFuture.completedFuture(null);
        } else {
            if (islandServer.equals(serverID)) {
                localIslandOperation.expelPlayer(islandUuid, playerUuid);
            } else {
                broker.sendRequest(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
            }
        }
    }

    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            String targetServer = selectServer();
            if (targetServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }
            if (targetServer.equals(serverID)) {
                return localIslandOperation.teleportToIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                return broker.sendRequest(targetServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, targetServer));
            }
        } else {
            if (islandServer.equals(serverID)) {
                return localIslandOperation.teleportToIsland(islandUuid, playerUuid, teleportLocation);
            } else {
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
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to send plugin message to player " + player.getName(), e);
            }
        }
    }
}
