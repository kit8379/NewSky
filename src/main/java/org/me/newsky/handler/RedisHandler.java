package org.me.newsky.handler;

import org.me.newsky.NewSky;

import redis.clients.jedis.Jedis;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class RedisHandler {
    private final Jedis jedis;
    private final NewSky plugin;
    private final ConfigHandler config;
    private final DatabaseHandler databaseHandler;

    public RedisHandler(String host, int port, String password, NewSky plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigHandler();
        this.databaseHandler = plugin.getDBHandler();
        this.jedis = new Jedis(host, port);
        if (password != null && !password.isEmpty()) {
            jedis.auth(password);
        }
    }

    public void connect() {
        jedis.connect();
    }

    public void disconnect() {
        jedis.close();
    }

    public Jedis getJedis() {
        return jedis;
    }

    public void updateWorldList() {
        String serverName = config.getServerName();
        Set<String> worldNames = new HashSet<>();
        File serverDirectory = new File(".");
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
    }

    public Set<String> getAllWorlds() {
        Set<String> allKeys = jedis.keys("*_worlds");
        Set<String> allWorlds = new HashSet<>();

        for (String key : allKeys) {
            allWorlds.addAll(jedis.smembers(key));
        }

        return allWorlds;
    }
}
