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
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return findServerByWorldName(islandName).thenCompose(optionalServerId -> {
            if (optionalServerId.isPresent()) {
                String targetServer = optionalServerId.get();
                if (targetServer.equals(serverID)) {
                    return islandOperation.loadWorld(islandName);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "loadIsland:" + islandName).thenApply(responses -> null);
                }
            } else {
                return CompletableFuture.failedFuture(new IllegalStateException("Island world not found on any server"));
            }
        });
    }

    @Override
    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString) {
        String islandName = "island-" + islandUuid.toString();
        return findServerByWorldName(islandName).thenCompose(optionalTargetServer -> {
            if (optionalTargetServer.isPresent()) {
                String targetServer = optionalTargetServer.get();
                if (targetServer.equals(serverID)) {
                    return islandOperation.teleportToWorld(islandName, player.getUniqueId().toString(), locationString);
                } else {
                    return islandPublishRequest.sendRequest(targetServer, "teleportToIsland:" + islandName + ":" + player.getUniqueId() + ":" + locationString).thenRun(() -> connectToServer(player, targetServer));
                }
            } else {
                return CompletableFuture.failedFuture(new IllegalStateException("Island world not found on any server"));
            }
        });
    }
}
