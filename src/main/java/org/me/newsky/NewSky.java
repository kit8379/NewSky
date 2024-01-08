package org.me.newsky;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.admin.AdminCommandExecutor;
import org.me.newsky.command.player.IslandCommandExecutor;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.event.WorldEventListener;
import org.me.newsky.island.IslandHandler;
import org.me.newsky.redis.RedisHandler;

import java.util.Objects;
import java.util.logging.Logger;

public class NewSky extends JavaPlugin {
    private Logger logger;
    private ConfigHandler config;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private IslandHandler islandHandler;


    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("Plugin enabling...");
        initalize();
        logger.info("Plugin enabled!");
    }

    private void initalize() {
        initializeConfig();
        checkDependencies("VoidGen", "Multiverse-Core");
        initializeRedis();
        initializeDatabase();
        initializeCache();
        initializeIslandHandler();
        registerListeners();
        registerCommands();
    }

    private void initializeConfig() {
        logger.info("Start loading configuration now...");
        try {
            config = new ConfigHandler(getConfig());
            logger.info("Config load success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Config load fail! Plugin will be disabled!");
        }
    }

    private void checkDependencies(String... pluginNames) {
        for (String pluginName : pluginNames) {
            Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin(pluginName);
            if (!(plugin != null && plugin.isEnabled())) {
                throw new IllegalStateException("Plugin " + pluginName + " is not enabled! Plugin will be disabled!");
            }
        }
    }

    private void initializeRedis() {
        logger.info("Start connecting to Redis now...");
        try {
            redisHandler = new RedisHandler(config);
            logger.info("Redis connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Fail! Plugin will be disabled!");
        }
    }

    private void initializeCache() {
        logger.info("Starting to cache into Redis");
        try {
            cacheHandler = new CacheHandler(logger, redisHandler, databaseHandler);
            cacheHandler.cacheAllDataToRedis();
            logger.info("Cache to Redis success");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Cache to Redis fail! Plugin will be disabled!");
        }
    }

    private void initializeDatabase() {
        logger.info("Start connecting to Database now...");
        try {
            databaseHandler = new DatabaseHandler(config);
            databaseHandler.createTables();
            logger.info("Database connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Database connection fail! Plugin will be disabled!");
        }
    }

    private void initializeIslandHandler() {
        logger.info("Starting island handler");
        try {
            islandHandler = new IslandHandler(this, logger, config);
            logger.info("Islands loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Islands load fail! Plugin will be disabled!");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WorldEventListener(cacheHandler, logger), this);
    }

    private void registerCommands() {
        Objects.requireNonNull(this.getCommand("islandadmin")).setExecutor(new AdminCommandExecutor(config, cacheHandler, islandHandler));
        Objects.requireNonNull(this.getCommand("island")).setExecutor(new IslandCommandExecutor(config, cacheHandler, islandHandler));
    }

    @Override
    public void onDisable() {
        logger.info("Plugin disabling...");
        shutdown();
        logger.info("Plugin disabled!");
    }

    public void shutdown() {
        redisHandler.disconnect();
        databaseHandler.close();
    }

    public void reload() {
        logger.info("Plugin reloading...");
        shutdown();
        initalize();
        logger.info("Plugin reloaded!");
    }
}
