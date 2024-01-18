package org.me.newsky.island;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.teleport.TeleportManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;
    private final String serverID;
    private final IslandOperation islandOperation;
    private final IslandPublishRequest islandPublishRequest;
    private final IslandSubscribeRequest islandSubscribeRequest;

    public IslandHandler(NewSky plugin, ConfigHandler config, MVWorldManager mvWorldManager, RedisHandler redisHandler, CacheHandler cacheHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.serverID = serverID;

        this.islandOperation = new IslandOperation(plugin, config, mvWorldManager, redisHandler, cacheHandler, teleportManager);
        this.islandPublishRequest = new IslandPublishRequest(plugin, redisHandler, heartBeatHandler, serverID);
        this.islandSubscribeRequest = new IslandSubscribeRequest(plugin, redisHandler, islandOperation, serverID);
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
                        plugin.debug("Island created on current server.");
                        islandOperation.createWorld(islandName)
                                .thenRun(() -> {
                                    createIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to create island on the server with the least number of worlds
                        plugin.debug("Island creation request sent to server: " + serverWithLeastWorlds);
                        islandPublishRequest.sendRequest("createIsland:" + serverWithLeastWorlds + ":" + islandName)
                                .thenRun(() -> {
                                    createIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    plugin.info("Failed to create island: " + ex.getMessage());
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
                        plugin.debug("Island loaded on current server.");
                        islandOperation.loadWorld(islandName)
                                .thenRun(() -> {
                                    loadIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to load the island on the server where it's located
                        plugin.debug("Island load request sent to server: " + serverByWorldName);
                        islandPublishRequest.sendRequest("loadIsland:" + serverByWorldName + ":" + islandName)
                                .thenRun(() -> {
                                    loadIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    plugin.info("Failed to load island: " + ex.getMessage());
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
                        plugin.debug("Island unloaded on current server.");
                        islandOperation.unloadWorld(islandName)
                                .thenRun(() -> {
                                    unloadIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to unload the island on the server where it's located
                        plugin.debug("Island unload request sent to server: " + serverByWorldName);
                        islandPublishRequest.sendRequest("unloadIsland:" + serverByWorldName + ":" + islandName)
                                .thenRun(() -> {
                                    unloadIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    plugin.info("Failed to unload island: " + ex.getMessage());
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
                        plugin.debug("Island deleted on current server.");
                        islandOperation.deleteWorld(islandName)
                                .thenRun(() -> {
                                    deleteIslandFuture.complete(null);
                                });
                    } else {
                        // Send the request to delete the island on the server where it's located
                        plugin.debug("Island delete request sent to server: " + serverByWorldName);
                        islandPublishRequest.sendRequest("deleteIsland:" + serverByWorldName + ":" + islandName)
                                .thenRun(() -> {
                                    deleteIslandFuture.complete(null);
                                });
                    }
                })
                .exceptionally(ex -> {
                    plugin.info("Failed to delete island: " + ex.getMessage());
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
                        plugin.debug("Teleporting to island on current server.");
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
                        plugin.debug("Island teleport request sent to server: " + serverByWorldName);
                        islandPublishRequest.sendRequest("teleportToIsland:" + serverByWorldName + ":" + islandName + ":" + player.getUniqueId())
                                .thenRun(() -> {
                                    connectToServer(player, serverByWorldName);
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
    }
    
    public void unsubscribeFromRequests() {
        islandSubscribeRequest.unsubscribeFromRequests();
    }
}