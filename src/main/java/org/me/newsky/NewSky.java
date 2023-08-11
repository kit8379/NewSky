package org.me.newsky;

import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.command.IslandAdminCommand;
import org.me.newsky.command.IslandCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisEventService;

import java.util.Objects;

public class NewSky extends JavaPlugin {
    private ConfigHandler config;
    private RedisHandler redisHandler;
    private RedisEventService redisEventService;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;

    @Override
    public void onEnable() {
        getLogger().info("Plugin enabling...");

        // Load Config
        getLogger().info("Start loading configuration now...");
        try {
            config = new ConfigHandler(this);
            getLogger().info("Config load success!");
        } catch (Exception e) {
            getLogger().info("Config load fail!");
            e.printStackTrace();
        }

        // Start Redis Connection
        getLogger().info("Start connecting to Redis now...");
        try {
            redisHandler = new RedisHandler(config.getRedisHost(), config.getRedisPort(), config.getRedisPassword(), this);
            redisEventService = new RedisEventService(redisHandler);
            getLogger().info("Redis connection success!");
        } catch (Exception e) {
            getLogger().info("Redis connection fail!");
            e.printStackTrace();
        }

        // Start Database connection
        getLogger().info("Start connecting to Database now...");
        try {
            databaseHandler = new DatabaseHandler(config.getDBHost(), config.getDBPort(), config.getDBName(), config.getDBUsername(), config.getDBPassword(), this);
            getLogger().info("Database connection success!");
        } catch (Exception e) {
            getLogger().info("Database connection fail!");
            e.printStackTrace();
        }

        // Start Caching
        getLogger().info("Starting to cache into Redis");
        try {
            cacheHandler = new CacheHandler(this);
            getLogger().info("Cache to Redis success");
        } catch (Exception e) {
            getLogger().info("Cache to Redis fail");
            e.printStackTrace();
        }

        Objects.requireNonNull(this.getCommand("island")).setExecutor(new IslandCommand(this));
        Objects.requireNonNull(this.getCommand("islandadmin")).setExecutor(new IslandAdminCommand(this));

        getLogger().info("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin disabling...");
        cacheHandler.saveCacheToDatabase();
        redisHandler.disconnect();
        databaseHandler.close();
        getLogger().info("Plugin disabled!");
    }

    public ConfigHandler getConfigHandler() {
        return config;
    }

    public RedisHandler getRedisHandler() {
        return redisHandler;
    }

    public RedisEventService getRedisEventService() {
        return redisEventService;
    }

    public DatabaseHandler getDBHandler() {
        return databaseHandler;
    }

    public CacheHandler getCacheHandler() {
        return cacheHandler;
    }
}
