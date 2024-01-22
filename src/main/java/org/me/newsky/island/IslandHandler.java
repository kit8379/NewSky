package org.me.newsky.island;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final NewSky plugin;
    private final HeartBeatHandler heartBeatHandler;
    private final String serverID;
    private final IslandOperation islandOperation;
    private final IslandPublishRequest islandPublishRequest;
    private final IslandSubscribeRequest islandSubscribeRequest;
    private final UpdatePublishRequest updatePublishRequest;
    private final UpdateSubscribeRequest updateSubscribeRequest;


    public IslandHandler(NewSky plugin, MVWorldManager mvWorldManager, RedisHandler redisHandler, CacheHandler cacheHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        this.plugin = plugin;
        this.heartBeatHandler = heartBeatHandler;
        this.serverID = serverID;

        this.islandOperation = new IslandOperation(plugin, mvWorldManager, cacheHandler, teleportManager);
        this.islandPublishRequest = new IslandPublishRequest(plugin, redisHandler, serverID);
        this.islandSubscribeRequest = new IslandSubscribeRequest(plugin, redisHandler, islandOperation, serverID);
        this.updatePublishRequest = new UpdatePublishRequest(plugin, redisHandler, heartBeatHandler, serverID);
        this.updateSubscribeRequest = new UpdateSubscribeRequest(plugin, redisHandler, islandOperation, serverID);
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();

        CompletableFuture<Void> createIslandFuture = new CompletableFuture<>();

        Set<String> activeServers = heartBeatHandler.getActiveServers();
        if (activeServers.isEmpty()) {
            createIslandFuture.completeExceptionally(new IllegalStateException("No active servers available"));
            return createIslandFuture;
        }

        // Select a random server from the active servers
        String selectedServer = activeServers.stream()
                .skip(new Random().nextInt(activeServers.size()))
                .findFirst()
                .orElse(null);

        if (selectedServer == null) {
            createIslandFuture.completeExceptionally(new IllegalStateException("Error selecting a random server"));
            return createIslandFuture;
        }

        if (selectedServer.equals(serverID)) {
            // Create the island on the current server
            plugin.debug("Island created on current server.");
            islandOperation.createWorld(islandName)
                    .thenRun(() -> {
                        createIslandFuture.complete(null);
                    });
        } else {
            // Send the request to create island on the selected server
            plugin.debug("Island creation request sent to server: " + selectedServer);
            islandPublishRequest.sendRequest(selectedServer, "createIsland:" + islandName)
                    .thenRun(() -> {
                        createIslandFuture.complete(null);
                    });
        }

        createIslandFuture.exceptionally(ex -> {
            plugin.info("Failed to create island: " + ex.getMessage());
            return null;
        });

        return createIslandFuture;
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();

        CompletableFuture<Void> deleteIslandFuture = new CompletableFuture<>();

        // Request the world list from all active servers
        updatePublishRequest.sendUpdateRequest()
                .thenCompose(worldListResponses -> {
                    // Process the responses to find the server where the island is located
                    String serverByWorldName = findServerByWorldName(islandName, worldListResponses);
                    if (serverByWorldName == null) {
                        throw new IllegalStateException("Island not found on any server");
                    }
                    return CompletableFuture.completedFuture(serverByWorldName);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Delete the island on the current server
                        plugin.debug("Island deleted on current server.");
                        islandOperation.deleteWorld(islandName)
                                .thenRun(() -> deleteIslandFuture.complete(null));
                    } else {
                        // Send the request to delete the island on the server where it's located
                        plugin.debug("Island delete request sent to server: " + serverByWorldName);
                        islandPublishRequest.sendRequest(serverByWorldName, "deleteIsland:" + islandName)
                                .thenRun(() -> deleteIslandFuture.complete(null));
                    }
                })
                .exceptionally(ex -> {
                    plugin.info("Failed to delete island: " + ex.getMessage());
                    deleteIslandFuture.completeExceptionally(ex);
                    return null;
                });

        return deleteIslandFuture;
    }


    public CompletableFuture<Void> teleportToIsland(Player player, UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        CompletableFuture<Void> teleportIslandFuture = new CompletableFuture<>();

        // Request the world list from all active servers
        updatePublishRequest.sendUpdateRequest()
                .thenCompose(worldListResponses -> {
                    // Process the responses to find the server where the island is located
                    String serverByWorldName = findServerByWorldName(islandName, worldListResponses);
                    if (serverByWorldName == null) {
                        throw new IllegalStateException("Island not found on any server");
                    }
                    return CompletableFuture.completedFuture(serverByWorldName);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Teleport to the island on the current server
                        plugin.debug("Island teleported to on current server.");
                        islandOperation.teleportToWorld(islandName, player.getUniqueId().toString())
                                .thenRun(() -> teleportIslandFuture.complete(null));
                    } else {
                        // Send the request to teleport to the island on the server where it's located
                        plugin.debug("Island teleport request sent to server: " + serverByWorldName);
                        islandPublishRequest.sendRequest(serverByWorldName, "teleportToIsland:" + islandName + ":" + player.getUniqueId())
                                .thenRun(() -> {
                                    // Connect to the server where the island is located
                                    connectToServer(player, serverByWorldName); // Assuming this method handles the server connection
                                    teleportIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    plugin.info("Failed to teleport to the island: " + ex.getMessage());
                    teleportIslandFuture.completeExceptionally(ex);
                    return null;
                });

        return teleportIslandFuture;
    }

    public void connectToServer(Player player, String serverName) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteArray);

        try {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            plugin.debug("Sending connect request to server: " + serverName);
        } catch (IOException e) {
            plugin.info("Failed to send connect request to server: " + e.getMessage());
        }
        player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
    }

    public void subscribeToRequests() {
        islandSubscribeRequest.subscribeToRequests();
        updateSubscribeRequest.subscribeToUpdateRequests();
    }

    public void unsubscribeFromRequests() {
        islandSubscribeRequest.unsubscribeFromRequests();
        updateSubscribeRequest.unsubscribeFromUpdateRequests();
    }

    private String findServerByWorldName(String worldName, Set<String> worldListResponses) {
        plugin.debug("Searching for server by world name: " + worldName);
        plugin.debug("Processing world list responses: " + worldListResponses);

        for (String response : worldListResponses) {
            String[] serverAndWorlds = response.split(":", 2);
            if (serverAndWorlds.length < 2) continue; // Skip if no worlds are listed
            String serverId = serverAndWorlds[0];
            String[] worlds = serverAndWorlds[1].split(",");
            for (String world : worlds) {
                if (world.equals(worldName)) {
                    return serverId;
                }
            }
        }
        return null;
    }
}