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
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class IslandHandler {

    private final NewSky plugin;
    private final String serverID;
    private final IslandOperation islandOperation;
    private final IslandPublishRequest islandPublishRequest;
    private final IslandSubscribeRequest islandSubscribeRequest;

    public IslandHandler(NewSky plugin, WorldHandler worldHandler, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        this.plugin = plugin;
        this.serverID = serverID;
        this.islandOperation = new IslandOperation(plugin, worldHandler, teleportManager);
        this.islandPublishRequest = new IslandPublishRequest(plugin, redisHandler, heartBeatHandler, serverID);
        this.islandSubscribeRequest = new IslandSubscribeRequest(plugin, redisHandler, islandOperation, serverID);
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return findServerWithLeastWorld()
                .thenCompose(targetServer -> {
                    if (targetServer.equals(serverID)) {
                        return islandOperation.createWorld(islandName);
                    } else {
                        return islandPublishRequest.sendRequest(targetServer, "createIsland:" + islandName)
                                .thenApply(responses -> null);
                    }
                });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return findServerByWorldName(islandName)
                .thenCompose(targetServer -> {
                    if (targetServer.equals(serverID)) {
                        return islandOperation.deleteWorld(islandName);
                    } else {
                        return islandPublishRequest.sendRequest(targetServer, "deleteIsland:" + islandName)
                                .thenApply(responses -> null);
                    }
                });
    }

    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString) {
        String islandName = "island-" + islandUuid.toString();
        return findServerByWorldName(islandName)
                .thenCompose(targetServer -> {
                    if (targetServer.equals(serverID)) {
                        return islandOperation.teleportToWorld(islandName, player.getUniqueId().toString(), locationString);
                    } else {
                        return islandPublishRequest.sendRequest(targetServer, "teleportToIsland:" + islandName + ":" + player.getUniqueId() + ":" + locationString)
                                .thenRun(() -> connectToServer(player, targetServer));
                    }
                });
    }

    private CompletableFuture<String> findServerByWorldName(String worldName) {
        return islandPublishRequest.sendRequest("all", "updateWorldList")
                .thenApply(worldListResponses -> {
                    for (Map.Entry<String, String> entry : worldListResponses.entrySet()) {
                        String serverId = entry.getKey();
                        String[] worlds = entry.getValue().split(",");
                        for (String world : worlds) {
                            if (world.equals(worldName)) {
                                return serverId;
                            }
                        }
                    }
                    throw new IllegalStateException("World not found on any server");
                });
    }

    private CompletableFuture<String> findServerWithLeastWorld() {
        return islandPublishRequest.sendRequest("all", "updateWorldList")
                .thenApply(worldListResponses -> {
                    return worldListResponses.entrySet().stream()
                            .min(Comparator.comparingInt(entry -> entry.getValue().split(",").length))
                            .map(Map.Entry::getKey)
                            .orElseThrow(() -> new IllegalStateException("No active server available"));
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

    public void subscribeToRequests() {
        islandSubscribeRequest.subscribeToRequests();
    }

    public void unsubscribeFromRequests() {
        islandSubscribeRequest.unsubscribeFromRequests();
    }
}
