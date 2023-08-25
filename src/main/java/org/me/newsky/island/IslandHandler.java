package org.me.newsky.island;

import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisHeartBeat;
import org.me.newsky.redis.RedisOperation;
import org.me.newsky.redis.RedisPublishRequest;

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

    public void createIsland(String islandName) {
        // Send the request
        redisPublishRequest.sendRequest("updateWorldList");

        // Set the callback
        redisPublishRequest.setCallback(() -> {
            // This code block will execute after the server responds
            Map<String, String> outputMap = new HashMap<>();
            // Fetch the server with the least number of worlds directly
            redisOpeartion.getServerWithLeastWorlds(() -> {
                String serverWithLeastWorlds = outputMap.get("serverWithLeastWorlds");

                logger.info("Server with the least number of worlds: " + serverWithLeastWorlds);

                if (serverWithLeastWorlds.equals(serverID)) {
                    // Create the island
                    redisOpeartion.createWorld(islandName, () -> {
                        // Do something with the output
                        logger.info("Island created.");
                    });
                } else {
                    // Send the request to the server with the least number of worlds
                    logger.info("Sending request to create island on server: " + serverWithLeastWorlds);
                    redisPublishRequest.sendRequest("createIsland:" + serverWithLeastWorlds + ":" + islandName);

                    // Set the callback
                    redisPublishRequest.setCallback(() -> {
                        // Do something with the output
                        logger.info("Island created.");
                    });
                }
            }, outputMap);
        });
    }


    public void loadIsland(String islandName) {
        redisPublishRequest.sendRequest("updateWorldList");

        // Set the callback
        redisPublishRequest.setCallback(() -> {
            // This code block will execute after the server responds
            Map<String, String> outputMap = new HashMap<>();
            // Fetch the server with the least number of worlds directly
            redisOpeartion.getServerByWorldName(islandName, () -> {
                String serverByWorldName = outputMap.get("serverByWorldName");

                logger.info("Server with the island: " + serverByWorldName);

                if (serverByWorldName.equals(serverID)) {
                    // Create the island
                    redisOpeartion.loadWorld(islandName, () -> {
                        // Do something with the output
                        logger.info("Island loaded.");
                    });
                } else {
                    // Send the request to the server with the least number of worlds
                    logger.info("Sending request to load island on server: " + serverByWorldName);
                    redisPublishRequest.sendRequest("loadIsland:" + serverByWorldName + ":" + islandName);

                    // Set the callback
                    redisPublishRequest.setCallback(() -> {
                        // Do something with the output
                        logger.info("Island loaded.");
                    });
                }
            }, outputMap);
        });
    }

    public void unloadIsland(String islandName) {
        redisPublishRequest.sendRequest("updateWorldList");

        // Set the callback
        redisPublishRequest.setCallback(() -> {
            // This code block will execute after the server responds
            Map<String, String> outputMap = new HashMap<>();
            // Fetch the server with the least number of worlds directly
            redisOpeartion.getServerByWorldName(islandName, () -> {
                String serverByWorldName = outputMap.get("serverByWorldName");

                logger.info("Server with the island: " + serverByWorldName);

                if (serverByWorldName.equals(serverID)) {
                    // Create the island
                    redisOpeartion.unloadWorld(islandName, () -> {
                        // Do something with the output
                        logger.info("Island unloaded.");
                    });
                } else {
                    // Send the request to the server with the least number of worlds
                    logger.info("Sending request to unload island on server: " + serverByWorldName);
                    redisPublishRequest.sendRequest("unloadIsland:" + serverByWorldName + ":" + islandName);

                    // Set the callback
                    redisPublishRequest.setCallback(() -> {
                        // Do something with the output
                        logger.info("Island unloaded.");
                    });
                }
            }, outputMap);
        });
    }

    public void deleteIsland(String islandName) {
        redisPublishRequest.sendRequest("updateWorldList");

        // Set the callback
        redisPublishRequest.setCallback(() -> {
            // This code block will execute after the server responds
            Map<String, String> outputMap = new HashMap<>();
            // Fetch the server with the least number of worlds directly
            redisOpeartion.getServerByWorldName(islandName, () -> {
                String serverByWorldName = outputMap.get("serverByWorldName");

                logger.info("Server with the island: " + serverByWorldName);

                if (serverByWorldName.equals(serverID)) {
                    // Create the island
                    redisOpeartion.deleteWorld(islandName, () -> {
                        // Do something with the output
                        logger.info("Island deleted.");
                    });
                } else {
                    // Send the request to the server with the least number of worlds
                    logger.info("Sending request to delete island on server: " + serverByWorldName);
                    redisPublishRequest.sendRequest("deleteIsland:" + serverByWorldName + ":" + islandName);

                    // Set the callback
                    redisPublishRequest.setCallback(() -> {
                        // Do something with the output
                        logger.info("Island deleted.");
                    });
                }
            }, outputMap);
        });
    }

    public void teleportToIsland(Player player, String islandName) {
        redisPublishRequest.sendRequest("updateWorldList");

        // Set the callback
        redisPublishRequest.setCallback(() -> {
            // This code block will execute after the server responds
            Map<String, String> outputMap = new HashMap<>();
            // Fetch the server with the least number of worlds directly
            redisOpeartion.getServerByWorldName(islandName, () -> {
                String serverByWorldName = outputMap.get("serverByWorldName");

                logger.info("Server with the island: " + serverByWorldName);

                if (serverByWorldName.equals(serverID)) {
                    // Teleport to the island
                    redisOpeartion.teleportToWorld(player.getName(), islandName, () -> {
                        // Do something with the output
                        logger.info("Teleporting player to island.");
                    });
                } else {
                    // Send the request to the server with the least number of worlds
                    logger.info("Sending request to teleport player to island on server: " + serverByWorldName);
                    redisPublishRequest.sendRequest("teleportToIsland:" + serverByWorldName + ":" + islandName);
                }
            }, outputMap);
        });
    }
}