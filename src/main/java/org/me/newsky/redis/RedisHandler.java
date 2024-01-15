package org.me.newsky.redis;

import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;

public class RedisHandler {

    private final JedisPool jedisPool;

    public RedisHandler(ConfigHandler config) {
        // Get Redis config
        String host = config.getRedisHost();
        int port = config.getRedisPort();
        String password = config.getRedisPassword();

        // Create JedisPool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        this.jedisPool = new JedisPool(poolConfig, host, port);
        try (Jedis jedis = jedisPool.getResource()) {
            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
            }
        }
    }

    // Publish a message to a channel
    public void publish(String channel, String message) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.publish(channel, message);
            }
        });
    }

    // Subscribe to a channel
    public void subscribe(JedisPubSub pubSub, String channel) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(pubSub, channel);
            }
        });
    }

    // Getter for JedisPool
    public JedisPool getJedisPool() {
        return jedisPool;
    }

    // Remember to close your JedisPool when your application ends
    public void disconnect() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }
}
