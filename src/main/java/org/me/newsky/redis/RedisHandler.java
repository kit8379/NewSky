package org.me.newsky.redis;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;

public class RedisHandler {

    private final int database;
    private final JedisPool jedisPool;

    public RedisHandler(NewSky plugin, ConfigHandler config) {

        // Get and set Redis config
        String host = config.getRedisHost();
        int port = config.getRedisPort();
        String password = config.getRedisPassword();
        this.database = config.getRedisDatabase();

        // Create JedisPool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        this.jedisPool = new JedisPool(poolConfig, host, port);
        try (Jedis jedis = jedisPool.getResource()) {
            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
                plugin.debug("Authenticated Redis connection");
            }
            jedis.select(database);
            plugin.debug("Selected Redis database " + database);
        }
    }

    // Publish a message to a channel
    public void publish(String channel, String message) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = getJedis()) {
                jedis.publish(channel, message);
            }
        });
    }

    // Subscribe to a channel
    public void subscribe(JedisPubSub pubSub, String channel) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = getJedis()) {
                jedis.subscribe(pubSub, channel);
            }
        });
    }

    public Jedis getJedis() {
        Jedis jedis = jedisPool.getResource();
        jedis.select(database);
        return jedis;
    }

    public void disconnect() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
