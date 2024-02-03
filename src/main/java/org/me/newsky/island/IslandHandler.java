package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class IslandHandler {

    protected NewSky plugin;
    protected String serverID;
    protected WorldHandler worldHandler;
    protected RedisHandler redisHandler;
    protected HeartBeatHandler heartBeatHandler;
    protected TeleportManager teleportManager;
    protected IslandOperation islandOperation;
    protected IslandPublishRequest islandPublishRequest;
    protected IslandSubscribeRequest islandSubscribeRequest;

    public IslandHandler(NewSky plugin, WorldHandler worldHandler, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        this.plugin = plugin;
        this.serverID = serverID;
        this.worldHandler = worldHandler;
        this.redisHandler = redisHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.teleportManager = teleportManager;
        this.islandOperation = new IslandOperation(plugin, worldHandler, teleportManager);
        this.islandPublishRequest = new IslandPublishRequest(plugin, redisHandler, heartBeatHandler, serverID);
        this.islandSubscribeRequest = new IslandSubscribeRequest(plugin, redisHandler, islandOperation, serverID);
    }

    public abstract CompletableFuture<Void> createIsland(UUID islandUuid);

    public abstract CompletableFuture<Void> loadIsland(UUID islandUuid);

    public abstract CompletableFuture<Void> unloadIsland(UUID islandUuid);

    public abstract CompletableFuture<Void> deleteIsland(UUID islandUuid);

    public abstract CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString);

    protected CompletableFuture<String> findServerByWorldName(String worldName) {
        return islandPublishRequest.sendRequest("all", "updateWorldList").thenApply(worldListResponses -> {
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

    protected CompletableFuture<String> findServerWithLeastWorld() {
        return islandPublishRequest.sendRequest("all", "updateWorldList").thenApply(worldListResponses -> {
            return worldListResponses.entrySet().stream().min(Comparator.comparingInt(entry -> {
                return entry.getValue().split(",").length;
            })).map(Map.Entry::getKey).orElseThrow(() -> {
                return new IllegalStateException("No active server available");
            });
        });
    }

    protected void connectToServer(Player player, String serverName) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(byteArray);
        try {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().severe("Could not send Connect request: " + e.getMessage());
        }
    }

    public void subscribeToRequests() {
        islandSubscribeRequest.subscribeToRequests();
    }

    public void unsubscribeFromRequests() {
        islandSubscribeRequest.unsubscribeFromRequests();
    }
}
