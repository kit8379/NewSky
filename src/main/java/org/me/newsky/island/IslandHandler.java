package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.post.PostIslandHandler;
import org.me.newsky.island.pubsub.IslandPublishRequest;
import org.me.newsky.island.pubsub.IslandSubscribeRequest;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class IslandHandler {

    protected NewSky plugin;

    protected ConfigHandler config;
    protected String serverID;
    protected WorldHandler worldHandler;
    protected RedisHandler redisHandler;
    protected HeartBeatHandler heartBeatHandler;
    protected TeleportManager teleportManager;
    protected PostIslandHandler postIslandHandler;
    protected IslandPublishRequest islandPublishRequest;
    protected IslandSubscribeRequest islandSubscribeRequest;

    public IslandHandler(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, WorldHandler worldHandler, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.serverID = serverID;
        this.worldHandler = worldHandler;
        this.redisHandler = redisHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.teleportManager = teleportManager;
        this.postIslandHandler = new PostIslandHandler(plugin, cacheHandler, worldHandler, teleportManager);
        this.islandPublishRequest = new IslandPublishRequest(plugin, redisHandler, heartBeatHandler, serverID);
        this.islandSubscribeRequest = new IslandSubscribeRequest(plugin, redisHandler, postIslandHandler, serverID);
    }

    public CompletableFuture<Void> createIsland(UUID islandUuid, UUID playerUuid, String spawnLocation) {
        String islandName = "island-" + islandUuid.toString();
        String playerName = playerUuid.toString();

        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> leastLoadedServer = findServerWithLeastWorld(worldListResponses);
            if (leastLoadedServer.isPresent()) {
                String targetServer = leastLoadedServer.get();
                if (targetServer.equals(serverID)) {
                    return postIslandHandler.createIsland(islandName, playerName, spawnLocation);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "createIsland:" + islandName + ":" + playerName + ":" + spawnLocation).thenApply(responses -> null);
                }
            } else {
                return CompletableFuture.failedFuture(new IllegalStateException(config.getNoActiveServerMessage()));
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
                    return postIslandHandler.deleteIsland(islandName);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "deleteIsland:" + islandName).thenApply(responses -> null);
                }
            } else {
                return postIslandHandler.deleteIsland(islandName);
            }
        });
    }

    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> serverId = findServerByWorldName(islandName, worldListResponses);
            if (serverId.isPresent()) {
                String targetServer = serverId.get();
                if (targetServer.equals(serverID)) {
                    return postIslandHandler.loadIsland(islandName);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "loadIsland:" + islandName).thenApply(responses -> null);
                }
            } else {
                Optional<String> leastLoadedServer = findServerWithLeastWorld(worldListResponses);
                if (leastLoadedServer.isPresent()) {
                    String targetServer = leastLoadedServer.get();
                    return islandPublishRequest.sendRequest(targetServer, "loadIsland:" + islandName).thenApply(responses -> null);
                } else {
                    return CompletableFuture.failedFuture(new IllegalStateException(config.getNoActiveServerMessage()));
                }
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
                    return postIslandHandler.unloadIsland(islandName);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "unloadIsland:" + islandName).thenApply(responses -> null);
                }
            } else {
                return CompletableFuture.failedFuture(new IllegalStateException(config.getIslandNotFoundInServerMessage()));
            }
        });
    }

    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, UUID playerUuid, String teleportLocation) {
        String islandName = "island-" + islandUuid.toString();
        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> serverId = findServerByWorldName(islandName, worldListResponses);
            if (serverId.isPresent()) {
                String targetServer = serverId.get();
                return proceedWithTeleportation(islandName, playerUuid.toString(), teleportLocation, targetServer);
            } else {
                Optional<String> leastLoadedServer = findServerWithLeastWorld(worldListResponses);
                if (leastLoadedServer.isPresent()) {
                    String targetServer = leastLoadedServer.get();
                    return proceedWithTeleportation(islandName, playerUuid.toString(), teleportLocation, targetServer);
                } else {
                    return CompletableFuture.failedFuture(new IllegalStateException(config.getNoActiveServerMessage()));
                }
            }
        });
    }

    public CompletableFuture<Void> lockIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> serverId = findServerByWorldName(islandName, worldListResponses);
            if (serverId.isPresent()) {
                String targetServer = serverId.get();
                if (targetServer.equals(serverID)) {
                    return postIslandHandler.lockIsland(islandName);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "lockIsland:" + islandName).thenApply(responses -> null);
                }
            } else {
                return CompletableFuture.completedFuture(null);
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


    protected CompletableFuture<Void> proceedWithTeleportation(String islandName, String playerUuid, String teleportLocation, String targetServer) {
        if (targetServer.equals(serverID)) {
            return postIslandHandler.teleportToIsland(islandName, playerUuid, teleportLocation);
        } else {
            return islandPublishRequest.sendRequest(targetServer, "teleportToIsland:" + islandName + ":" + playerUuid + ":" + teleportLocation).thenRun(() -> {
                connectToServer(Objects.requireNonNull(Bukkit.getPlayer(UUID.fromString(playerUuid))), targetServer);
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
}