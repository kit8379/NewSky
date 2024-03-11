package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.post.IslandOperation;
import org.me.newsky.island.post.IslandPublishRequest;
import org.me.newsky.island.post.IslandSubscribeRequest;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public abstract class IslandHandler {

    protected NewSky plugin;

    protected ConfigHandler config;
    protected String serverID;
    protected WorldHandler worldHandler;
    protected RedisHandler redisHandler;
    protected HeartBeatHandler heartBeatHandler;
    protected TeleportManager teleportManager;
    protected IslandOperation islandOperation;
    protected IslandPublishRequest islandPublishRequest;
    protected IslandSubscribeRequest islandSubscribeRequest;

    public IslandHandler(NewSky plugin, ConfigHandler config, WorldHandler worldHandler, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.serverID = serverID;
        this.worldHandler = worldHandler;
        this.redisHandler = redisHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.teleportManager = teleportManager;
        this.islandOperation = new IslandOperation(plugin, worldHandler, teleportManager);
        this.islandPublishRequest = new IslandPublishRequest(plugin, redisHandler, heartBeatHandler, serverID);
        this.islandSubscribeRequest = new IslandSubscribeRequest(plugin, redisHandler, islandOperation, serverID);
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> leastLoadedServer = findServerWithLeastWorld(worldListResponses);
            if (leastLoadedServer.isPresent()) {
                String targetServer = leastLoadedServer.get();
                if (targetServer.equals(serverID)) {
                    return islandOperation.createWorld(islandName);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "createIsland:" + islandName).thenApply(responses -> null);
                }
            } else {
                return CompletableFuture.failedFuture(new IllegalStateException(config.getNoActiveServerMessage()));
            }
        });
    }

    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> serverId = findServerByWorldName(islandName, worldListResponses);
            if (serverId.isPresent()) {
                String targetServer = serverId.get();
                if (targetServer.equals(serverID)) {
                    return islandOperation.unloadWorld(islandName);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "unloadIsland:" + islandName).thenApply(responses -> null);
                }
            } else {
                return CompletableFuture.failedFuture(new IllegalStateException(config.getIslandNotFoundInServerMessage()));
            }
        });
    }

    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> serverId = findServerByWorldName(islandName, worldListResponses);
            if (serverId.isPresent()) {
                String targetServer = serverId.get();
                if (targetServer.equals(serverID)) {
                    return islandOperation.deleteWorld(islandName);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "deleteIsland:" + islandName).thenApply(responses -> null);
                }
            } else {
                return islandOperation.deleteWorld(islandName);
            }
        });
    }

    public CompletableFuture<ConcurrentHashMap<String, String>> fetchWorldList() {
        return islandPublishRequest.sendRequest("all", "updateWorldList");
    }

    protected Optional<String> findServerByWorldName(String worldName, Map<String, String> worldListResponses) {
        for (Map.Entry<String, String> entry : worldListResponses.entrySet()) {
            String serverId = entry.getKey();
            String[] worlds = entry.getValue().split(",");
            for (String world : worlds) {
                if (world.equals(worldName)) {
                    return Optional.of(serverId);
                }
            }
        }
        return Optional.empty();
    }

    protected Optional<String> findServerWithLeastWorld(Map<String, String> worldListResponses) {
        return worldListResponses.entrySet().stream().min(Comparator.comparingInt(entry -> {
            return entry.getValue().split(",").length;
        })).map(Map.Entry::getKey);
    }


    protected CompletableFuture<Void> proceedWithTeleportation(String islandName, Player player, String locationString, String targetServer) {
        if (targetServer.equals(serverID)) {
            return islandOperation.teleportToWorld(islandName, player.getUniqueId().toString(), locationString);
        } else {
            return islandPublishRequest.sendRequest(targetServer, "teleportToIsland:" + islandName + ":" + player.getUniqueId() + ":" + locationString).thenRun(() -> {
                connectToServer(player, targetServer);
            });
        }
    }

    protected void connectToServer(Player player, String serverName) {
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

    public void subscribeToRequests() {
        islandSubscribeRequest.subscribeToRequests();
    }

    public void unsubscribeFromRequests() {
        islandSubscribeRequest.unsubscribeFromRequests();
    }

    public abstract CompletableFuture<Void> loadIsland(UUID islandUuid);

    public abstract CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString);

}