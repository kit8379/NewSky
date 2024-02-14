package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DynamicIslandHandler extends IslandHandler {

    public DynamicIslandHandler(NewSky plugin, ConfigHandler config, WorldHandler worldHandler, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        super(plugin, config, worldHandler, redisHandler, heartBeatHandler, teleportManager, serverID);
    }

    @Override
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        String islandName = "island-" + islandUuid.toString();
        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> serverId = findServerByWorldName(islandName, worldListResponses);
            if (serverId.isPresent()) {
                String targetServer = serverId.get();
                if (targetServer.equals(serverID)) {
                    return islandOperation.loadWorld(islandName);
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

    @Override
    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString) {
        String islandName = "island-" + islandUuid.toString();
        return fetchWorldList().thenCompose(worldListResponses -> {
            Optional<String> serverId = findServerByWorldName(islandName, worldListResponses);
            if (serverId.isPresent()) {
                String targetServer = serverId.get();
                return proceedWithTeleportation(islandName, player, locationString, targetServer);
            } else {
                Optional<String> leastLoadedServer = findServerWithLeastWorld(worldListResponses);
                if (leastLoadedServer.isPresent()) {
                    String targetServer = leastLoadedServer.get();
                    return proceedWithTeleportation(islandName, player, locationString, targetServer);
                } else {
                    return CompletableFuture.failedFuture(new IllegalStateException(config.getNoActiveServerMessage()));
                }
            }
        });
    }
}
