package org.me.newsky.redis;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.*;

import java.util.concurrent.CompletableFuture;

public class RedisHandler {

    private final NewSky plugin;
    private final ConnectionPool pool;

    public RedisHandler(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;

        String host = config.getRedisHost();
        int port = config.getRedisPort();
        String password = config.getRedisPassword();
        int database = config.getRedisDatabase();

        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();

        DefaultJedisClientConfig.Builder clientConfigBuilder = DefaultJedisClientConfig.builder().database(database).connectionTimeoutMillis(2000).socketTimeoutMillis(2000);

        if (password != null && !password.isEmpty()) {
            clientConfigBuilder.password(password);
            plugin.info("Connected to Redis at " + host + ":" + port + " with password.");
        } else {
            plugin.info("Connected to Redis at " + host + ":" + port + " without password.");
        }

        JedisClientConfig clientConfig = clientConfigBuilder.build();

        // Correct constructor
        this.pool = new ConnectionPool(new HostAndPort(host, port), clientConfig, poolConfig);
    }

    // Publish
    public void publish(String channel, String message) {
        try (Jedis jedis = getJedis()) {
            jedis.publish(channel, message);
            plugin.debug("RedisHandler", "Published message to channel: " + channel);
        }
    }

    // Subscribe
    public void subscribe(JedisPubSub pubSub, String channel) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = getJedis()) {
                jedis.subscribe(pubSub, channel);
                plugin.debug("RedisHandler", "Subscribed to channel: " + channel);
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    public Jedis getJedis() {
        Connection connection = pool.getResource();
        return new Jedis(connection);
    }

    public void disconnect() {
        if (pool != null) {
            pool.close();
            plugin.info("Disconnected from Redis.");
        }
    }
}