package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.network.BasePublishRequest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


/**
 * Handles pre-island operations such as creating, deleting, loading, unloading, and locking islands.
 * This class is responsible for sending requests to the appropriate server to perform these operations.
 */
public class PreIslandHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final BasePublishRequest publishRequest;
    private final PostIslandHandler postIslandHandler;
    private final String serverID;

    public PreIslandHandler(NewSky plugin, CacheHandler cacheHandler, BasePublishRequest publishRequest, PostIslandHandler postIslandHandler, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.publishRequest = publishRequest;
        this.postIslandHandler = postIslandHandler;
        this.serverID = serverID;
    }


    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String targetServer = getRandomServer();
        if (targetServer == null) {
            future.completeExceptionally(new NoActiveServerException());
        } else {
            if (targetServer.equals(serverID)) {
                postIslandHandler.createIsland(islandUuid).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Created island " + islandUuid + " on the current server");
                });
            } else {
                publishRequest.sendRequest(targetServer, "create", islandUuid.toString()).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Created island " + islandUuid + " on server " + targetServer);
                });
            }
        }
        return future;
    }


    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String targetServer = getServerByIsland(islandUuid);
        if (targetServer == null) {
            future.completeExceptionally(new NoActiveServerException());
        } else {
            if (targetServer.equals(serverID)) {
                postIslandHandler.deleteIsland(islandUuid).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Deleted island " + islandUuid + " on the current server");
                });
            } else {
                publishRequest.sendRequest(targetServer, "delete", islandUuid.toString()).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Deleted island " + islandUuid + " on server " + targetServer);
                });
            }
        }
        return future;
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String targetServer = getServerByIsland(islandUuid);
        if (targetServer == null) {
            future.completeExceptionally(new NoActiveServerException());
        } else {
            if (targetServer.equals(serverID)) {
                postIslandHandler.loadIsland(islandUuid).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Loaded island " + islandUuid + " on the current server");
                });
            } else {
                publishRequest.sendRequest(targetServer, "load", islandUuid.toString()).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Loaded island " + islandUuid + " on server " + targetServer);
                });
            }
        }
        return future;
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String targetServer = getServerByIsland(islandUuid);
        if (targetServer == null) {
            future.completeExceptionally(new NoActiveServerException());
        } else {
            if (targetServer.equals(serverID)) {
                postIslandHandler.unloadIsland(islandUuid).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Unloaded island " + islandUuid + " on the current server");
                });
            } else {
                publishRequest.sendRequest(targetServer, "unload", islandUuid.toString()).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Unloaded island " + islandUuid + " on server " + targetServer);
                });
            }
        }
        return future;
    }

    public void lockIsland(UUID islandUuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String targetServer = getServerByIsland(islandUuid);
        if (targetServer == null) {
            future.completeExceptionally(new NoActiveServerException());
        } else {
            if (targetServer.equals(serverID)) {
                postIslandHandler.lockIsland(islandUuid).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Locked island " + islandUuid + " on the current server");
                });
            } else {
                publishRequest.sendRequest(targetServer, "lock", islandUuid.toString()).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Locked island " + islandUuid + " on server " + targetServer);
                });
            }
        }
    }


    public CompletableFuture<Void> teleportToIsland(UUID playerUuid, UUID islandUuid, String teleportLocation) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String targetServer = getServerByIsland(islandUuid);
        if (targetServer == null) {
            future.completeExceptionally(new NoActiveServerException());
        } else {
            if (targetServer.equals(serverID)) {
                postIslandHandler.teleportToIsland(islandUuid, playerUuid, teleportLocation).thenAccept(result -> {
                    future.complete(null);
                    plugin.debug("Teleported player " + playerUuid + " to island " + islandUuid + " on the current server");
                });
            } else {
                publishRequest.sendRequest(targetServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenAccept(result -> {
                    connectToServer(playerUuid, targetServer);
                    future.complete(null);
                    plugin.debug("Teleported player " + playerUuid + " to island " + islandUuid + " on server " + targetServer);
                });
            }
        }
        return future;
    }


    /**
     * Get a random server from the list of active servers
     *
     * @return A random server name
     */
    private String getRandomServer() {
        Map<String, String> activeServers = cacheHandler.getActiveServers();
        if (activeServers.isEmpty()) {
            return null;
        }
        String[] serverNames = activeServers.keySet().toArray(new String[0]);
        int randomIndex = new Random().nextInt(serverNames.length);
        return serverNames[randomIndex];
    }


    /**
     * Get the server that is currently loaded with the specified island
     *
     * @param islandUuid The UUID of the island
     * @return The server name that is currently loaded with the island
     */
    private String getServerByIsland(UUID islandUuid) {
        String serverName = cacheHandler.getIslandLoadedServer(islandUuid);
        if (serverName == null || serverName.isEmpty()) {
            return getRandomServer();
        }
        return serverName;
    }


    /**
     * Connect a player to a server
     *
     * @param playerUuid The UUID of the player
     * @param serverName The name of the server
     */
    private void connectToServer(UUID playerUuid, String serverName) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            try {
                out.writeUTF("Connect");
                out.writeUTF(serverName);
                player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
