package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisHeartBeat;
import org.me.newsky.redis.RedisOperation;
import org.me.newsky.redis.RedisPublishRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class IslandHandler {

    private final Logger logger;
    private final String serverID;
    private final RedisOperation redisOperation;
    private final RedisPublishRequest redisPublishRequest;

    public IslandHandler(Logger logger, ConfigHandler config, RedisHandler redisHandler, RedisHeartBeat redisHeartBeat, RedisOperation redisOperation) {
        this.logger = logger;
        this.serverID = config.getServerName();
        this.redisOperation = redisOperation;
        this.redisPublishRequest = new RedisPublishRequest(redisHandler, redisHeartBeat, serverID);
    }

    public CompletableFuture<Void> createIsland(String islandName) {
        CompletableFuture<Void> createIslandFuture = new CompletableFuture<>();

        // Send the request to update world list
        redisPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server with the least number of worlds
                    return redisOperation.getServerWithLeastWorlds(outputMap);
                })
                .thenAccept(serverWithLeastWorlds -> {
                    if (serverWithLeastWorlds.equals(serverID)) {
                        // Create the island on the current server
                        redisOperation.createWorld(islandName)
                                .thenRun(() -> {
                                    logger.info("Island created on current server.");
                                    createIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to create island on the server with the least number of worlds
                        redisPublishRequest.sendRequest("createIsland:" + serverWithLeastWorlds + ":" + islandName)
                                .thenRun(() -> {
                                    logger.info("Island creation request sent to server: " + serverWithLeastWorlds);
                                    createIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    logger.severe("Failed to create island: " + ex.getMessage());
                    createIslandFuture.completeExceptionally(ex);
                    return null;
                });

        return createIslandFuture;
    }


    public CompletableFuture<Void> loadIsland(String islandName) {
        CompletableFuture<Void> loadIslandFuture = new CompletableFuture<>();

        // Send the request to update world list
        redisPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server where the island is located
                    return redisOperation.getServerByWorldName(islandName, outputMap);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Load the island on the current server
                        redisOperation.loadWorld(islandName)
                                .thenRun(() -> {
                                    logger.info("Island loaded on current server.");
                                    loadIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to load the island on the server where it's located
                        redisPublishRequest.sendRequest("loadIsland:" + serverByWorldName + ":" + islandName)
                                .thenRun(() -> {
                                    logger.info("Island load request sent to server: " + serverByWorldName);
                                    loadIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    logger.severe("Failed to load island: " + ex.getMessage());
                    loadIslandFuture.completeExceptionally(ex);
                    return null;
                });

        return loadIslandFuture;
    }

    public CompletableFuture<Void> unloadIsland(String islandName) {
        CompletableFuture<Void> unloadIslandFuture = new CompletableFuture<>();

        // Send the request to update world list
        redisPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server where the island is located
                    return redisOperation.getServerByWorldName(islandName, outputMap);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Unload the island on the current server
                        redisOperation.unloadWorld(islandName)
                                .thenRun(() -> {
                                    logger.info("Island unloaded on current server.");
                                    unloadIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to unload the island on the server where it's located
                        redisPublishRequest.sendRequest("unloadIsland:" + serverByWorldName + ":" + islandName)
                                .thenRun(() -> {
                                    logger.info("Island unload request sent to server: " + serverByWorldName);
                                    unloadIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    logger.severe("Failed to unload island: " + ex.getMessage());
                    unloadIslandFuture.completeExceptionally(ex);
                    return null;
                });

        return unloadIslandFuture;
    }

    public CompletableFuture<Void> deleteIsland(String islandName) {
        CompletableFuture<Void> deleteIslandFuture = new CompletableFuture<>();

        // Send the request to update world list
        redisPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server where the island is located
                    return redisOperation.getServerByWorldName(islandName, outputMap);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Delete the island on the current server
                        redisOperation.deleteWorld(islandName)
                                .thenRun(() -> {
                                    logger.info("Island deleted on current server.");
                                    deleteIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to delete the island on the server where it's located
                        redisPublishRequest.sendRequest("deleteIsland:" + serverByWorldName + ":" + islandName)
                                .thenRun(() -> {
                                    logger.info("Island delete request sent to server: " + serverByWorldName);
                                    deleteIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    logger.severe("Failed to delete island: " + ex.getMessage());
                    deleteIslandFuture.completeExceptionally(ex);
                    return null;
                });

        return deleteIslandFuture;
    }

    public void teleportToIsland(Player player, String islandName) {
        CompletableFuture<Void> teleportFuture = new CompletableFuture<>();

        // Send the request to update world list
        redisPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server where the island is located
                    return redisOperation.getServerByWorldName(islandName, outputMap);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Teleport the player to the island on the current server
                        redisOperation.teleportToWorld(islandName, player.getName())
                                .thenRun(() -> {
                                    logger.info("Player teleported to island on current server.");
                                    teleportFuture.complete(null);
                                });
                    } else {
                        // Send the request to teleport the player to the island on the server where it's located
                        connectToServer(player, serverByWorldName);
                        redisPublishRequest.sendRequest("teleportToIsland:" + serverByWorldName + ":" + islandName + ":" + player.getName())
                                .thenRun(() -> {
                                    logger.info("Teleport request sent to server: " + serverByWorldName);
                                    teleportFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    logger.severe("Failed to teleport to island: " + ex.getMessage());
                    teleportFuture.completeExceptionally(ex);
                    return null;
                });

    }

    public void connectToServer(Player player, String serverName) {

    }
}