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

        // Get and set Redis config
        String host = config.getRedisHost();
        int port = config.getRedisPort();
        String password = config.getRedisPassword();

        // Create JedisPool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        this.jedisPool = new JedisPool(poolConfig, host, port);
        try (Jedis jedis = jedisPool.getResource()) {
            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
                plugin.debug("Authenticated Redis connection");
            }
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
