package org.me.newsky.redis;

import org.bukkit.Bukkit;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.io.File;
import java.util.*;

public class RedisOperationsHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final IslandHandler islandHandler;
    private final RedisConnectionHandler redisConnectionHandler;

    public RedisOperationsHandler(NewSky plugin, ConfigHandler config, IslandHandler islandHandler, RedisConnectionHandler redisConnectionHandler) {
        this.plugin = plugin;
        this.config = config;
        this.islandHandler = islandHandler;
        this.redisConnectionHandler = redisConnectionHandler;

        // Subscribe to Redis channel
        subscribeMessage();
    }

    // ... [All other methods remain the same]

    public void publishMessage(String channel, String message) {
        try (Jedis jedis = redisConnectionHandler.getJedisPool().getResource()) {
            jedis.publish(channel, message);
        }
    }

    // ... [Other methods related to Redis operations]

    // Note: You might want to refactor some other methods as well to improve clarity and separation of concerns.
}
