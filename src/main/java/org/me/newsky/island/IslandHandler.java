package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class IslandHandler {
    private final Logger logger;
    private final String serverID;
    private final RedisOperation redisOpeartion;
    private final RedisPublishRequest redisPublishRequest;

    public IslandHandler(NewSky plugin, Logger logger, ConfigHandler config, RedisHandler redisHandler, RedisHeartBeat redisHeartBeat, RedisOperation redisOperation) {
        this.logger = logger;
        this.serverID = config.getServerName();
        this.redisOpeartion = redisOperation;
        this.redisPublishRequest = new RedisPublishRequest(plugin, config, redisHandler, redisHeartBeat);
    }

    @FunctionalInterface
    interface IslandAction {
        void apply(String serverByWorldName, String islandName);
    }

    private void executeIslandAction(String islandName, IslandAction action) {
        redisPublishRequest.sendRequest("updateWorldList");

        // Set the callback
        redisPublishRequest.setCallback(() -> {
            Map<String, String> outputMap = new HashMap<>();
            redisOpeartion.getServerByWorldName(islandName, () -> {
                String serverByWorldName = outputMap.get("serverByWorldName");
                action.apply(serverByWorldName, islandName);
            }, outputMap);
        });
    }

    public void createIsland(String islandName) {
        executeIslandAction(islandName, (serverByWorldName, island) -> {
            if (serverByWorldName.equals(serverID)) {
                redisOpeartion.createWorld(island, () -> logger.info("Island created."));
            } else {
                logger.info("Sending request to create island on server: " + serverByWorldName);
                redisPublishRequest.sendRequest("createIsland:" + serverByWorldName + ":" + island);
                redisPublishRequest.setCallback(() -> logger.info("Island created."));
            }
        });
    }

    public void loadIsland(String islandName) {
        executeIslandAction(islandName, (serverByWorldName, island) -> {
            if (serverByWorldName.equals(serverID)) {
                redisOpeartion.loadWorld(island, () -> logger.info("Island loaded."));
            } else {
                logger.info("Sending request to load island on server: " + serverByWorldName);
                redisPublishRequest.sendRequest("loadIsland:" + serverByWorldName + ":" + island);
                redisPublishRequest.setCallback(() -> logger.info("Island loaded."));
            }
        });
    }

    public void unloadIsland(String islandName) {
        executeIslandAction(islandName, (serverByWorldName, island) -> {
            if (serverByWorldName.equals(serverID)) {
                redisOpeartion.unloadWorld(island, () -> logger.info("Island unloaded."));
            } else {
                logger.info("Sending request to unload island on server: " + serverByWorldName);
                redisPublishRequest.sendRequest("unloadIsland:" + serverByWorldName + ":" + island);
                redisPublishRequest.setCallback(() -> logger.info("Island unloaded."));
            }
        });
    }

    public void deleteIsland(String islandName) {
        executeIslandAction(islandName, (serverByWorldName, island) -> {
            if (serverByWorldName.equals(serverID)) {
                redisOpeartion.deleteWorld(island, () -> logger.info("Island deleted."));
            } else {
                logger.info("Sending request to delete island on server: " + serverByWorldName);
                redisPublishRequest.sendRequest("deleteIsland:" + serverByWorldName + ":" + island);
                redisPublishRequest.setCallback(() -> logger.info("Island deleted."));
            }
        });
    }

    public void teleportToIsland(Player player, String islandName) {
        executeIslandAction(islandName, (serverByWorldName, island) -> {
            if (serverByWorldName.equals(serverID)) {
                redisOpeartion.teleportToWorld(player.getName(), island, () -> logger.info("Teleporting player to island."));
            } else {
                logger.info("Sending request to teleport player to island on server: " + serverByWorldName);
                redisPublishRequest.sendRequest("teleportToIsland:" + serverByWorldName + ":" + island);
            }
        });
    }
}
