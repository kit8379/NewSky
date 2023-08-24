package org.me.newsky;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.admin.AdminCommandExecutor;
import org.me.newsky.command.player.IslandCommandExecutor;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.event.WorldEventListener;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisHeartBeat;
import org.me.newsky.redis.RedisPubSubResponse;

import java.util.Objects;
import java.util.logging.Logger;

public class NewSky extends JavaPlugin {
    private Logger logger;
    private RedisHandler redisHandler;
    private RedisHeartBeat redisHeartBeat;
    private RedisPubSubResponse redisPubSubResponse;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private IslandHandler islandHandler;
    private MVWorldManager mvWorldManager;
    private ConfigHandler config;

    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("Plugin enabling...");
        initalize();
        logger.info("Plugin enabled!");
    }

    private void initalize() {
        initializeConfig();
        checkDependencies("Multiverse-Core", "VoidGen");
        initializeRedis();
        initializeDatabase();
        initializeCache();
        initializeMVWorldManager();
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
            throw new IllegalStateException("Config load fail!");
        }
    }

    private void checkDependencies(String... pluginNames) {
        for (String pluginName : pluginNames) {
            Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin(pluginName);
            if (!(plugin != null && plugin.isEnabled())) {
                throw new IllegalStateException(pluginName + " not found! Plugin will be disabled!");
            }
        }
    }

    private void initializeRedis() {
        logger.info("Start connecting to Redis now...");
        try {
            redisHandler = new RedisHandler(this, logger, config);
            redisHeartBeat = new RedisHeartBeat(this, logger, config, redisHandler);
            redisHeartBeat.startHeartBeat();
            redisHeartBeat.listenForHeartBeats();
            redisPubSubResponse = new RedisPubSubResponse(this, logger, config, redisHandler, redisHeartBeat);
            logger.info("Redis connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Fail! Plugin will be disabled!");
        }
    }

    private void initializeDatabase() {
        logger.info("Start connecting to Database now...");
        try {
            databaseHandler = new DatabaseHandler(this, logger, config);
            databaseHandler.createTables();
            logger.info("Database connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Database connection fail! Plugin will be disabled!");
        }
    }

    private void initializeCache() {
        logger.info("Starting to cache into Redis");
        try {
            cacheHandler = new CacheHandler(logger, redisHandler, databaseHandler);
            cacheHandler.cacheDataToRedis();
            logger.info("Cache to Redis success");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Cache to Redis fail! Plugin will be disabled!");
        }
    }

    private void initializeMVWorldManager() {
        logger.info("Starting MVmanager");
        try {
            mvWorldManager = ((MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core")).getMVWorldManager();
            logger.info("MVmanager loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("MVmanager load fail! Plugin will be disabled!");
        }
    }

    private void initializeIslandHandler() {
        logger.info("Starting island handler");
        try {
            islandHandler = new IslandHandler(logger, config, mvWorldManager, redisHandler);
            logger.info("Islands loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Islands load fail! Plugin will be disabled!");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WorldEventListener(logger), this);
    }

    private void registerCommands() {
        Objects.requireNonNull(this.getCommand("islandadmin")).setExecutor(new AdminCommandExecutor(cacheHandler, islandHandler));
        Objects.requireNonNull(this.getCommand("island")).setExecutor(new IslandCommandExecutor(cacheHandler, islandHandler));
    }

    @Override
    public void onDisable() {
        logger.info("Plugin disabling...");
        shutdown();
        logger.info("Plugin disabled!");
    }

    public void shutdown() {
        logger.info("Start saving cache to database now...");
        cacheHandler.saveCacheToDatabase(); // TODO: Make IT in Sync
        logger.info("Cache saved to database!");
        logger.info("Start disconnecting from Redis now...");
        redisHandler.disconnect();
        redisHeartBeat.stopHeartBeat();
        logger.info("Redis disconnected!");
        logger.info("Start disconnecting from Database now...");
        databaseHandler.close();
        logger.info("Database disconnected!");
    }

    public void reload() {
        logger.info("Plugin reloading...");
        shutdown();
        initalize();
        logger.info("Plugin reloaded!");
    }
}
