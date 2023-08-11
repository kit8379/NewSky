package org.me.newsky.redis;

import org.me.newsky.NewSky;

import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RedisHandler {
    private final JedisPool jedisPool;
    private final NewSky plugin;
    private final ConfigHandler config;
    private final DatabaseHandler databaseHandler;

    public RedisHandler(String host, int port, String password, NewSky plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigHandler();
        this.databaseHandler = plugin.getDBHandler();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10); // Adjust as needed

        this.jedisPool = new JedisPool(poolConfig, host, port);
        // Authenticating all connections from the pool, if needed
        try (Jedis jedis = jedisPool.getResource()) {
            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
            }
        }
    }

    public void disconnect() {
        jedisPool.close();
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public void updateWorldList() {
        try (Jedis jedis = getJedisPool().getResource()) {
            String serverName = config.getServerName();
            Set<String> worldNames = new HashSet<>();

            // Get the server directory using Bukkit's method
            File serverDirectory = plugin.getServer().getWorldContainer();

            File[] files = serverDirectory.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        File levelDat = new File(file, "level.dat");
                        if (levelDat.exists()) {
                            worldNames.add(file.getName());
                        }
                    }
                }
            }

            String key = serverName + "_worlds";
            jedis.del(key);
            jedis.sadd(key, worldNames.toArray(new String[0]));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<String, Set<String>> getAllWorlds() {
        Map<String, Set<String>> serverWorlds = new HashMap<>();

        try (Jedis jedis = getJedisPool().getResource()) {
            Set<String> allKeys = jedis.keys("*_worlds");

            for (String key : allKeys) {
                String serverName = key.split("_worlds")[0]; // Extracting server name from the key
                Set<String> worlds = jedis.smembers(key);
                serverWorlds.put(serverName, worlds);
            }

            return serverWorlds;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
