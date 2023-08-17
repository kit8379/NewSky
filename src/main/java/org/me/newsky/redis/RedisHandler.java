package org.me.newsky.redis;

import org.bukkit.Bukkit;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.io.File;
import java.util.*;

public class RedisHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final IslandHandler islandHandler;
    private final JedisPool jedisPool;

    public RedisHandler(String host, int port, String password, int maxTotal,  NewSky plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigHandler();
        this.islandHandler = plugin.getIslandHandler();

        // Create JedisPool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        this.jedisPool = new JedisPool(poolConfig, host, port);
        try (Jedis jedis = jedisPool.getResource()) {
            if (password != null && !password.isEmpty()) {
                jedis.auth(password);
            }
        }

        // Subscribe to Redis channel
        subscribeMessage();
    }

    public String findServerWithLeastWorlds() {
        publishMessage(plugin.getName(), "all:updateList");
        try {
            Thread.sleep(1000);  // Sleep for 1 second (1000 milliseconds)
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Map<String, Set<String>> allServersWorlds = getAllWorlds();

        if (allServersWorlds == null || allServersWorlds.isEmpty()) {
            return null;
        }

        return allServersWorlds.entrySet().stream()
                .min(Comparator.comparingInt(entry -> entry.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }


    public String findServerWithWorld(String worldName) {
        publishMessage(plugin.getName(), "all:updateList");
        try {
            Thread.sleep(1000);  // Sleep for 1 second (1000 milliseconds)
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Map<String, Set<String>> allServersWorlds = getAllWorlds();

        if (allServersWorlds == null || allServersWorlds.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Set<String>> entry : allServersWorlds.entrySet()) {
            if (entry.getValue().contains(worldName)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public void publishMessage(String channel, String message) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.publish(channel, message);
        }
    }

    private void subscribeMessage() {
        JedisPubSub jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                String[] parts = message.split(":");
                String serverName = parts[0];
                String operation = parts[1];
                String worldName = parts.length > 2 ? parts[2] : null;
                String playerName = parts.length > 3 ? parts[3] : null;

                switch (operation) {
                    case "updateList":
                        if(!(serverName.equals("all"))) {
                            break;
                        }
                        updateWorldList();
                        break;

                    case "createWorld":
                        if (!(serverName.equals(config.getServerName()))) {
                            break;
                        }
                        islandHandler.createWorldOperation(worldName);
                        break;

                    case "loadWorld":
                        if (!(serverName.equals(config.getServerName()))) {
                            break;
                        }
                        islandHandler.loadWorldOperation(worldName);
                        break;

                    case "unloadWorld":
                        if (!(serverName.equals(config.getServerName()))) {
                            break;
                        }
                        islandHandler.unloadWorldOperation(worldName);
                        break;

                    case "deleteWorld":
                        if (!(serverName.equals(config.getServerName()))) {
                            break;
                        }
                        islandHandler.deleteWorldOperation(worldName);
                        break;

                    case "teleportToSpawn":
                        if (!(serverName.equals(config.getServerName()))) {
                            break;
                        }
                        islandHandler.teleportToSpawnOperation(worldName, playerName);
                        break;

                    default:
                        Bukkit.getLogger().warning("Received unknown operation: " + operation);
                        break;
                }
            }
        };

        new Thread(() -> {
            try (Jedis jedis = getJedisPool().getResource()) {
                jedis.subscribe(jedisPubSub, plugin.getName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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

    // Remember to close your JedisPool when your application ends
    public void disconnect() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    // Getter for JedisPool
    public JedisPool getJedisPool() {
        return jedisPool;
    }
}
