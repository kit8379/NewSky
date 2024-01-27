package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandHandler {

    private final NewSky plugin;
    private final HeartBeatHandler heartBeatHandler;
    private final String serverID;
    private final IslandOperation islandOperation;
    private final IslandPublishRequest islandPublishRequest;
    private final IslandSubscribeRequest islandSubscribeRequest;


    public IslandHandler(NewSky plugin, WorldHandler worldHandler, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        this.plugin = plugin;
        this.heartBeatHandler = heartBeatHandler;
        this.serverID = serverID;

        this.islandOperation = new IslandOperation(plugin, worldHandler, teleportManager);
        this.islandPublishRequest = new IslandPublishRequest(plugin, redisHandler, heartBeatHandler, serverID);
        this.islandSubscribeRequest = new IslandSubscribeRequest(plugin, redisHandler, islandOperation, serverID);
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        Set<String> activeServers = heartBeatHandler.getActiveServers();

        if (activeServers.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("No active servers available"));
        }

        String selectedServer = activeServers.stream()
                .skip(new Random().nextInt(activeServers.size()))
                .findFirst()
                .orElse(null);

        if (selectedServer == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Error selecting a random server"));
        }

        if (selectedServer.equals(serverID)) {
            plugin.debug("Island created on current server.");
            return islandOperation.createWorld(islandName);
        } else {
            plugin.debug("Island creation request sent to server: " + selectedServer);
            return islandPublishRequest.sendRequest(selectedServer, "createIsland:" + islandName)
                    .thenApply(responses -> null);
        }
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        CompletableFuture<Void> deleteIslandFuture = new CompletableFuture<>();

        islandPublishRequest.sendRequest("all", "updateWorldList")
                .thenCompose(worldListResponses -> {
                    String serverByWorldName = findServerByWorldName(islandName, worldListResponses);
                    if (serverByWorldName == null) {
                        return CompletableFuture.failedFuture(new IllegalStateException("Island not found on any server"));
                    }

                    if (serverByWorldName.equals(serverID)) {
                        return islandOperation.deleteWorld(islandName);
                    } else {
                        return islandPublishRequest.sendRequest(serverByWorldName, "deleteIsland:" + islandName)
                                .thenApply(responses -> null);
                    }
                })
                .thenRun(() -> deleteIslandFuture.complete(null))
                .exceptionally(ex -> {
                    plugin.info("Failed to delete island: " + ex.getMessage());
                    deleteIslandFuture.completeExceptionally(ex);
                    return null;
                });

        return deleteIslandFuture;
    }


    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString) {
        String islandName = "island-" + islandUuid.toString();
        CompletableFuture<Void> teleportIslandFuture = new CompletableFuture<>();

        // Request the world list from all active servers
        islandPublishRequest.sendRequest("all", "updateWorldList").thenCompose(worldListResponses -> {
            // Process the responses to find the server where the island is located
            String serverByWorldName = findServerByWorldName(islandName, worldListResponses);
            if (serverByWorldName == null) {
                throw new IllegalStateException("Island not found on any server");
            }
            return CompletableFuture.completedFuture(serverByWorldName);
        }).thenAccept(serverByWorldName -> {
            if (serverByWorldName.equals(serverID)) {
                // Teleport to the island on the current server
                plugin.debug("Island teleported to on current server.");
                islandOperation.teleportToWorld(islandName, player.getUniqueId().toString(), locationString).thenRun(() -> {
                    teleportIslandFuture.complete(null);
                });
            } else {
                // Send the request to teleport to the island on the server where it's located
                plugin.debug("Island teleport request sent to server: " + serverByWorldName);
                islandPublishRequest.sendRequest(serverByWorldName, "teleportToIsland:" + islandName + ":" + player.getUniqueId() + ":" + locationString).thenAccept(responses -> {
                    // Handle responses here, if needed
                    connectToServer(player, serverByWorldName); // Assuming this method handles the server connection
                    teleportIslandFuture.complete(null);
                });
            }
        }).exceptionally(ex -> {
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

    private String findServerByWorldName(String worldName, ConcurrentHashMap<String, String> worldListResponses) {
        plugin.debug("Searching for server by world name: "
                + worldName);
        plugin.debug("Processing world list responses: " + worldListResponses);

        for (Map.Entry<String, String> entry : worldListResponses.entrySet()) {
            String serverId = entry.getKey();
            String[] worlds = entry.getValue().split(",");
            for (String world : worlds) {
                if (world.equals(worldName)) {
                    return serverId;
                }
            }
        }
        return null;
    }

    public void subscribeToRequests() {
        islandSubscribeRequest.subscribeToRequests();
    }

    public void unsubscribeFromRequests() {
        islandSubscribeRequest.unsubscribeFromRequests();
    }
}