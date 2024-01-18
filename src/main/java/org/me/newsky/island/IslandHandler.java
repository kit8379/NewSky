package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisHeartBeat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class IslandHandler {

    private final NewSky plugin;
    private final Logger logger;
    private final CacheHandler cacheHandler;
    private final IslandOperation islandOperation;
    private final String serverID;
    private final IslandPublishRequest islandPublishRequest;

    public IslandHandler(NewSky plugin, Logger logger, RedisHandler redisHandler, CacheHandler cacheHandler, RedisHeartBeat redisHeartBeat, IslandOperation islandOperation, String serverID) {
        this.plugin = plugin;
        this.logger = logger;
        this.cacheHandler = cacheHandler;
        this.islandOperation = islandOperation;
        this.serverID = serverID;
        this.islandPublishRequest = new IslandPublishRequest(logger, redisHandler, redisHeartBeat, serverID);
    }

    public CompletableFuture<Void> createIsland(String islandName) {
        CompletableFuture<Void> createIslandFuture = new CompletableFuture<>();

        // Send the request to update world list
        islandPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server with the least number of worlds
                    return islandOperation.getServerWithLeastWorlds(outputMap);
                })
                .thenAccept(serverWithLeastWorlds -> {
                    if (serverWithLeastWorlds.equals(serverID)) {
                        // Create the island on the current server
                        islandOperation.createWorld(islandName)
                                .thenRun(() -> {
                                    logger.info("Island created on current server.");
                                    createIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to create island on the server with the least number of worlds
                        islandPublishRequest.sendRequest("createIsland:" + serverWithLeastWorlds + ":" + islandName)
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
        islandPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server where the island is located
                    return islandOperation.getServerByWorldName(islandName, outputMap);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Load the island on the current server
                        islandOperation.loadWorld(islandName)
                                .thenRun(() -> {
                                    logger.info("Island loaded on current server.");
                                    loadIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to load the island on the server where it's located
                        islandPublishRequest.sendRequest("loadIsland:" + serverByWorldName + ":" + islandName)
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
        islandPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server where the island is located
                    return islandOperation.getServerByWorldName(islandName, outputMap);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Unload the island on the current server
                        islandOperation.unloadWorld(islandName)
                                .thenRun(() -> {
                                    logger.info("Island unloaded on current server.");
                                    unloadIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to unload the island on the server where it's located
                        islandPublishRequest.sendRequest("unloadIsland:" + serverByWorldName + ":" + islandName)
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
        islandPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server where the island is located
                    return islandOperation.getServerByWorldName(islandName, outputMap);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Delete the island on the current server
                        islandOperation.deleteWorld(islandName)
                                .thenRun(() -> {
                                    logger.info("Island deleted on current server.");
                                    deleteIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to delete the island on the server where it's located
                        islandPublishRequest.sendRequest("deleteIsland:" + serverByWorldName + ":" + islandName)
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

    public CompletableFuture<Void> teleportToIsland(Player player, String islandName) {
        CompletableFuture<Void> teleportIslandFuture = new CompletableFuture<>();

        // Send the request to update world list
        islandPublishRequest.sendRequest("updateWorldList")
                .thenCompose(v -> {
                    Map<String, String> outputMap = new HashMap<>();
                    // Fetch the server where the island is located
                    return islandOperation.getServerByWorldName(islandName, outputMap);
                })
                .thenAccept(serverByWorldName -> {
                    if (serverByWorldName.equals(serverID)) {
                        // Teleport to the island on the current server
                        Optional<String> islandSpawn = cacheHandler.getPlayerIslandSpawn(player.getUniqueId(), UUID.fromString(islandName));

                        if (islandSpawn.isEmpty()) {
                            islandSpawn = Optional.of("0,100,0,0,0");
                        }

                        String[] parts = islandSpawn.get().split(",");
                        double x = Double.parseDouble(parts[0]);
                        double y = Double.parseDouble(parts[1]);
                        double z = Double.parseDouble(parts[2]);
                        float yaw = Float.parseFloat(parts[3]);
                        float pitch = Float.parseFloat(parts[4]);

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Location location = new Location(Bukkit.getWorld(islandName), x, y, z, yaw, pitch);
                            player.teleport(location);
                            teleportIslandFuture.complete(null);
                        });
                    } else {
                        // Send the request to teleport to the island on the server where it's located
                        islandPublishRequest.sendRequest("teleportToIsland:" + serverByWorldName + ":" + islandName + ":" + player.getUniqueId())
                                .thenRun(() -> {
                                    connectToServer(player, serverByWorldName);
                                    logger.info("Island teleport request sent to server: " + serverByWorldName);
                                    teleportIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    logger.severe("Failed to teleport to the island: " + ex.getMessage());
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
        } catch (IOException e) {
            e.printStackTrace();
        }

        player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
    }
}