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
            return islandOperation.createWorld(islandName);
        } else {
            return islandPublishRequest.sendRequest(selectedServer, "createIsland:" + islandName)
                    .thenApply(responses -> null);
        }
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();

        return islandPublishRequest.sendRequest("all", "updateWorldList")
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
                });
    }

    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString) {
        String islandName = "island-" + islandUuid.toString();

        return islandPublishRequest.sendRequest("all", "updateWorldList").thenCompose(worldListResponses -> {
            String serverByWorldName = findServerByWorldName(islandName, worldListResponses);

            if (serverByWorldName == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Island not found on any server"));
            }

            if (serverByWorldName.equals(serverID)) {
                return islandOperation.teleportToWorld(islandName, player.getUniqueId().toString(), locationString);
            } else {
                return islandPublishRequest.sendRequest(serverByWorldName, "teleportToIsland:" + islandName + ":" + player.getUniqueId() + ":" + locationString)
                        .thenRun(() -> connectToServer(player, serverByWorldName));
            }
        });
    }

    public void connectToServer(Player player, String serverName) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteArray);
        try {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
    }

    private String findServerByWorldName(String worldName, ConcurrentHashMap<String, String> worldListResponses) {
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