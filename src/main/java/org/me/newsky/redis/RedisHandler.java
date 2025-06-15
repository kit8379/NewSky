package org.me.newsky.redis;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;

public class RedisHandler {

    private final NewSky plugin;
    private final JedisPool jedisPool;

    public RedisHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;

        // Get Redis config
        String host = config.getRedisHost();
        int port = config.getRedisPort();
        String password = config.getRedisPassword();
        int database = config.getRedisDatabase();

        // Create JedisPool with database selection
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        if (password != null && !password.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, password, database);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, null, database);
        }
    }


    // Publish a message to a channel
    public void publish(String channel, String message) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = getJedis()) {
                jedis.publish(channel, message);
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    // Subscribe to a channel
    public void subscribe(JedisPubSub pubSub, String channel) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = getJedis()) {
                jedis.subscribe(pubSub, channel);
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    public Jedis getJedis() {
        return jedisPool.getResource();
    }

    public void disconnect() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
