package org.me.newsky.redis;

import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisHandler {

    private final ConfigHandler config;
    private final JedisPool jedisPool;

    public RedisHandler(ConfigHandler config) {
        this.config = config;

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
