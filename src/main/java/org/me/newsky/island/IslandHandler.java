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

public class IslandHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandOperation islandOperation;
    private final String serverID;
    private final IslandPublishRequest islandPublishRequest;

    public IslandHandler(NewSky plugin, RedisHandler redisHandler, CacheHandler cacheHandler, RedisHeartBeat redisHeartBeat, IslandOperation islandOperation, String serverID) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.islandOperation = islandOperation;
        this.serverID = serverID;
        this.islandPublishRequest = new IslandPublishRequest(redisHandler, redisHeartBeat, serverID);
    }

    public CompletableFuture<Void> createIsland(String islandName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Step 0: Send request to update world list on a different server
        String updateOperation = "updateWorldList";
        CompletableFuture<Void> updateFuture = islandPublishRequest.sendRequest(updateOperation);

        updateFuture.thenRun(() -> {
            // Once updateWorldList is complete, proceed with original logic
            // Step 1: Find the server with the least worlds
            Map<String, String> outputMap = new HashMap<>();
            CompletableFuture<String> serverFuture = islandOperation.getServerWithLeastWorlds(outputMap);

            serverFuture.thenAccept(serverID -> {
                // Step 2: Create the island on the determined server
                if (serverID.equals(this.serverID)) {
                    // Case 1: Create the island on the current server
                    islandOperation.createWorld(islandName).thenRun(() -> future.complete(null));
                } else {
                    // Case 2: Send request to create the island on a different server
                    String operation = "createIsland:" + islandName;
                    islandPublishRequest.sendRequest(operation).thenRun(() -> future.complete(null));
                }
            }).exceptionally(e -> {
                future.completeExceptionally(e);
                return null;
            });
        }).exceptionally(e -> {
            // Handle exceptions from updateWorldList request
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }


    public CompletableFuture<Void> loadIsland(String islandName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Step 0: Send request to update world list on a different server
        String updateOperation = "updateWorldList";
        CompletableFuture<Void> updateFuture = islandPublishRequest.sendRequest(updateOperation);

        updateFuture.thenRun(() -> {
            // Once updateWorldList is complete, proceed with original logic
            // Step 1: Find the server where the island is located
            Map<String, String> outputMap = new HashMap<>();
            CompletableFuture<String> serverFuture = islandOperation.getServerByWorldName(islandName, outputMap);

            serverFuture.thenAccept(serverID -> {
                // Step 2: Load the island on the determined server
                if (serverID.equals(this.serverID)) {
                    // Case 1: Load the island on the current server
                    islandOperation.loadWorld(islandName).thenRun(() -> future.complete(null));
                } else {
                    // Case 2: Send request to load the island on a different server
                    String operation = "loadIsland:" + islandName;
                    islandPublishRequest.sendRequest(operation).thenRun(() -> future.complete(null));
                }
            }).exceptionally(e -> {
                future.completeExceptionally(e);
                return null;
            });
        }).exceptionally(e -> {
            // Handle exceptions from updateWorldList request
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }

    public CompletableFuture<Void> unloadIsland(String islandName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Step 0: Send request to update world list on a different server
        String updateOperation = "updateWorldList";
        CompletableFuture<Void> updateFuture = islandPublishRequest.sendRequest(updateOperation);

        updateFuture.thenRun(() -> {
            // Once updateWorldList is complete, proceed with original logic
            // Step 1: Find the server where the island is located
            Map<String, String> outputMap = new HashMap<>();
            CompletableFuture<String> serverFuture = islandOperation.getServerByWorldName(islandName, outputMap);

            serverFuture.thenAccept(serverID -> {
                // Step 2: Unload the island on the determined server
                if (serverID.equals(this.serverID)) {
                    // Case 1: Unload the island on the current server
                    islandOperation.unloadWorld(islandName).thenRun(() -> future.complete(null));
                } else {
                    // Case 2: Send request to unload the island on a different server
                    String operation = "unloadIsland:" + islandName;
                    islandPublishRequest.sendRequest(operation).thenRun(() -> future.complete(null));
                }
            }).exceptionally(e -> {
                future.completeExceptionally(e);
                return null;
            });
        }).exceptionally(e -> {
            // Handle exceptions from updateWorldList request
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }


    public CompletableFuture<Void> deleteIsland(String islandName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Step 0: Send request to update world list on a different server
        String updateOperation = "updateWorldList";
        CompletableFuture<Void> updateFuture = islandPublishRequest.sendRequest(updateOperation);

        updateFuture.thenRun(() -> {
            // Once updateWorldList is complete, proceed with original logic
            // Step 1: Find the server where the island is located
            Map<String, String> outputMap = new HashMap<>();
            CompletableFuture<String> serverFuture = islandOperation.getServerByWorldName(islandName, outputMap);

            serverFuture.thenAccept(serverID -> {
                // Step 2: Delete the island on the determined server
                if (serverID.equals(this.serverID)) {
                    // Case 1: Delete the island on the current server
                    islandOperation.deleteWorld(islandName).thenRun(() -> future.complete(null));
                } else {
                    // Case 2: Send request to delete the island on a different server
                    String operation = "deleteIsland:" + islandName;
                    islandPublishRequest.sendRequest(operation).thenRun(() -> future.complete(null));
                }
            }).exceptionally(e -> {
                future.completeExceptionally(e);
                return null;
            });
        }).exceptionally(e -> {
            // Handle exceptions from updateWorldList request
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }

    public CompletableFuture<Void> teleportToIsland(Player player, String islandName) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Step 0: Send request to update world list on a different server
        String updateOperation = "updateWorldList";
        CompletableFuture<Void> updateFuture = islandPublishRequest.sendRequest(updateOperation);

        updateFuture.thenRun(() -> {
            // Step 1: Find the server where the island is located
            Map<String, String> outputMap = new HashMap<>();
            CompletableFuture<String> serverFuture = islandOperation.getServerByWorldName(islandName, outputMap);

            serverFuture.thenAccept(serverID -> {
                if (serverID.equals(this.serverID)) {
                    // Step 2: Teleport to the island on the current server
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
                        future.complete(null);
                    });
                } else {
                    // Step 3: Send request to teleport to the island on a different server
                    String operation = "teleportToIsland:" + islandName + ":" + player.getUniqueId();
                    islandPublishRequest.sendRequest(operation).thenRun(() -> {
                        connectToServer(player, serverID);
                        future.complete(null);
                    });
                }
            }).exceptionally(e -> {
                future.completeExceptionally(e);
                return null;
            });
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
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