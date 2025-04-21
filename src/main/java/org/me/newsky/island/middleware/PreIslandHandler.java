package org.me.newsky.island.middleware;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandNotLoadedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.network.RedisPublishRequest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;


/**
 * Handles pre-island operations such as creating, deleting, loading, unloading, and locking islands.
 * This class is responsible for sending requests to the appropriate server to perform these operations.
 */
public class PreIslandHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final RedisPublishRequest publishRequest;
    private final PostIslandHandler postIslandHandler;
    private final String serverID;

    public PreIslandHandler(NewSky plugin, CacheHandler cacheHandler, RedisPublishRequest publishRequest, PostIslandHandler postIslandHandler, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.publishRequest = publishRequest;
        this.postIslandHandler = postIslandHandler;
        this.serverID = serverID;
    }


    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String randomServer = getRandomServer();
        if (randomServer == null) {
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }
        if (randomServer.equals(serverID)) {
            return postIslandHandler.createIsland(islandUuid);
        } else {
            return publishRequest.sendRequest(randomServer, "create", islandUuid.toString());
        }
    }


    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            String randomServer = getRandomServer();
            if (randomServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }
            if (randomServer.equals(serverID)) {
                return postIslandHandler.deleteIsland(islandUuid);
            } else {
                return publishRequest.sendRequest(randomServer, "delete", islandUuid.toString());
            }
        } else if (islandServer.equals(serverID)) {
            return postIslandHandler.deleteIsland(islandUuid);
        } else {
            return publishRequest.sendRequest(islandServer, "delete", islandUuid.toString());
        }
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            String randomServer = getRandomServer();
            if (randomServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }
            if (randomServer.equals(serverID)) {
                return postIslandHandler.loadIsland(islandUuid);
            } else {
                return publishRequest.sendRequest(randomServer, "load", islandUuid.toString());
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
                return postIslandHandler.unloadIsland(islandUuid);
            } else {
                return publishRequest.sendRequest(islandServer, "unload", islandUuid.toString());
            }
        }
    }

    public void lockIsland(UUID islandUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            CompletableFuture.completedFuture(null);
        } else {
            if (islandServer.equals(serverID)) {
                postIslandHandler.lockIsland(islandUuid);
            } else {
                publishRequest.sendRequest(islandServer, "lock", islandUuid.toString());
            }
        }
    }

    public void expelPlayer(UUID islandUuid, UUID playerUuid) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            CompletableFuture.completedFuture(null);
        } else {
            if (islandServer.equals(serverID)) {
                postIslandHandler.expelPlayer(islandUuid, playerUuid);
            } else {
                publishRequest.sendRequest(islandServer, "expel", islandUuid.toString(), playerUuid.toString());
            }
        }
    }

    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        String islandServer = getServerByIsland(islandUuid);
        if (islandServer == null) {
            String randomServer = getRandomServer();
            if (randomServer == null) {
                return CompletableFuture.failedFuture(new NoActiveServerException());
            }
            if (randomServer.equals(serverID)) {
                return postIslandHandler.teleportToIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                return publishRequest.sendRequest(randomServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, randomServer));
            }
        } else {
            if (islandServer.equals(serverID)) {
                return postIslandHandler.teleportToIsland(islandUuid, playerUuid, teleportLocation);
            } else {
                return publishRequest.sendRequest(islandServer, "teleport", islandUuid.toString(), playerUuid.toString(), teleportLocation).thenRun(() -> connectToServer(playerUuid, islandServer));
            }
        }
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
            return null;
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
                plugin.getLogger().log(Level.SEVERE, "Failed to send plugin message to player " + player.getName(), e);
            }
        }
    }
}