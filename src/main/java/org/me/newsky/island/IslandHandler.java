package org.me.newsky.island;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.redis.RedisHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class IslandHandler {

    private final Logger logger;
    private final CacheHandler cacheHandler;
    private final HeartBeatHandler heartBeatHandler;
    private final IslandPublishRequest islandPublishRequest;
    private final String serverName;

    public IslandHandler(Logger logger, ConfigHandler config, RedisHandler redisHandler, CacheHandler cacheHandler, HeartBeatHandler heartBeatHandler) {
        this.logger = logger;
        this.cacheHandler = cacheHandler;
        this.heartBeatHandler = heartBeatHandler;
        this.serverName = config.getServerName();

        this.islandPublishRequest = new IslandPublishRequest(redisHandler);
        IslandSubscribeRequest islandSubscribeRequest = new IslandSubscribeRequest(this, redisHandler, serverName);
    }

    public void createIsland(UUID playerUuid) {
        ConcurrentHashMap<String, Instant> activeServers = heartBeatHandler.getActiveServers();

        if (activeServers.isEmpty()) {
            logger.warning("No active servers available to create the island.");
            return;
        }

        // Select a random server from the active servers list
        ArrayList<String> serverNames = new ArrayList<>(activeServers.keySet());
        String randomServerID = serverNames.get(ThreadLocalRandom.current().nextInt(serverNames.size()));

        // Publish a create island request to the selected server
        islandPublishRequest.publishCreateIslandRequest(randomServerID, playerUuid.toString());
    }

    public void postCreateIsland(UUID playerUuid) {
        // Create the island
        UUID islandUuid = UUID.randomUUID();
        cacheHandler.createIsland(islandUuid);

        // Add the player to the island as an owner
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
        cacheHandler.addIslandPlayer(islandUuid, player.getUniqueId(), "0,100,0" ,"owner");

        // Teleport the player to the island
        teleportToIsland(playerUuid, islandUuid);
    }


    public void loadIsland(String islandName) {

    }

    public void unloadIsland(String islandName) {

    }

    public void deleteIsland(String islandName) {

    }

    public void teleportToIsland(UUID playerUuid, UUID islandUuid) {

    }
}