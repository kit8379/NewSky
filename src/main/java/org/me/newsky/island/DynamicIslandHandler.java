package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DynamicIslandHandler extends IslandHandler {

    public DynamicIslandHandler(NewSky plugin, WorldHandler worldHandler, RedisHandler redisHandler, HeartBeatHandler heartBeatHandler, TeleportManager teleportManager, String serverID) {
        super(plugin, worldHandler, redisHandler, heartBeatHandler, teleportManager, serverID);
    }

    @Override
    public CompletableFuture<Void> createIsland(UUID islandUuid) {
        // TODO: Implement createIsland for dynamic mode
        return null;
    }

    @Override
    public CompletableFuture<Void> loadIsland(UUID islandUuid) {
        // TODO: Implement loadIsland for dynamic mode
        return null;
    }

    @Override
    public CompletableFuture<Void> unloadIsland(UUID islandUuid) {
        // TODO: Implement unloadIsland for dynamic mode
        return null;
    }

    @Override
    public CompletableFuture<Void> deleteIsland(UUID islandUuid) {
        // TODO: Implement deleteIsland for dynamic mode
        return null;
    }

    @Override
    public CompletableFuture<Void> teleportToIsland(UUID islandUuid, Player player, String locationString) {
        // TODO: Implement teleportToIsland for dynamic mode
        return null;
    }
}
