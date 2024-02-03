package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StaticIslandHandler extends IslandHandler {

    public StaticIslandHandler(NewSky plugin, WorldHandler worldHandler, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        super(plugin, worldHandler, redisHandler, heartBeatHandler, teleportManager, serverID);
    }

    @Override
    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return findServerWithLeastWorld().thenCompose(targetServer -> {
            if (targetServer.equals(serverID)) {
                return islandOperation.createWorld(islandName);
            } else {
                return islandPublishRequest.sendRequest(targetServer, "createIsland:" + islandName).thenApply(responses -> {
                    return null;
                });
            }
        });
    }

    @Override
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return findServerByWorldName(islandName).thenCompose(targetServer -> {
            if (targetServer.equals(serverID)) {
                return islandOperation.loadWorld(islandName);
            } else {
                return islandPublishRequest.sendRequest(targetServer, "loadIsland:" + islandName).thenApply(responses -> {
                    return null;
                });
            }
        });
    }

    @Override
    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return findServerByWorldName(islandName).thenCompose(targetServer -> {
            if (targetServer.equals(serverID)) {
                return islandOperation.unloadWorld(islandName);
            } else {
                return islandPublishRequest.sendRequest(targetServer, "unloadIsland:" + islandName).thenApply(responses -> {
                    return null;
                });
            }
        });
    }

    @Override
    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return findServerByWorldName(islandName).thenCompose(targetServer -> {
            if (targetServer.equals(serverID)) {
                return islandOperation.deleteWorld(islandName);
            } else {
                return islandPublishRequest.sendRequest(targetServer, "deleteIsland:" + islandName).thenApply(responses -> {
                    return null;
                });
            }
        });
    }

    @Override
    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString) {
        String islandName = "island-" + islandUuid.toString();
        return findServerByWorldName(islandName).thenCompose(targetServer -> {
            if (targetServer.equals(serverID)) {
                return islandOperation.teleportToWorld(islandName, player.getUniqueId().toString(), locationString);
            } else {
                return islandPublishRequest.sendRequest(targetServer, "teleportToIsland:" + islandName + ":" + player.getUniqueId() + ":" + locationString).thenRun(() -> {
                    connectToServer(player, targetServer);
                });
            }
        });
    }
}
