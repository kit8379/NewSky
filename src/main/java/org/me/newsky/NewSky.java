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
import org.me.newsky.island.IslandHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisHeartBeat;
import org.me.newsky.redis.RedisOperation;
import org.me.newsky.redis.RedisSubscribeRequest;

import java.util.Objects;
import java.util.logging.Logger;

public class NewSky extends JavaPlugin {
    private Logger logger;
    private RedisHandler redisHandler;
    private RedisHeartBeat redisHeartBeat;
    private RedisOperation redisOpeartion;
    private RedisSubscribeRequest redisSubscribeRequest;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private IslandHandler islandHandler;
    private MVWorldManager mvWorldManager;
    private ConfigHandler config;
    private String serverID;

    @Override
    public void onEnable() {
        logger = getLogger();
        logger.info("Plugin enabling...");
        initalize();
        logger.info("Plugin enabled!");
    }

    private void initalize() {
        checkDependencies("Multiverse-Core", "VoidGen");
        initializeConfig();
        initalizeServerID();
        initializeMVWorldManager();
        initializeRedis();
        initializeDatabase();
        initializeCache();
        initalizeRedisHeartBeat();
        initalizeRedisOperation();
        initalizeRedisSubscribeRequest();
        initializeIslandHandler();
        registerListeners();
        registerCommands();
    }

    private void checkDependencies(String... pluginNames) {
        for (String pluginName : pluginNames) {
            Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin(pluginName);
            if (!(plugin != null && plugin.isEnabled())) {
                throw new IllegalStateException(pluginName + " not found! Plugin will be disabled!");
            }
        }
    }

    private void initializeConfig() {
        logger.info("Start loading configuration now...");
        try {
            saveDefaultConfig();
            config = new ConfigHandler(getConfig());
            logger.info("Config load success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Config load fail!");
        }
    }

    private void initalizeServerID() {
        logger.info("Start loading server ID now...");
        try {
            serverID = config.getServerName();
            logger.info("Server ID load success!");
            logger.info("This Server ID: " + serverID);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Server ID load fail!");
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

    private void initalizeRedisHeartBeat() {
        logger.info("Start connecting to Redis Heart Beat now...");
        try {
            redisHeartBeat = new RedisHeartBeat(this, redisHandler, serverID);
            // Only start the heartbeat if the server is in island mode
            if (config.getServerMode().equalsIgnoreCase("island")) {
                redisHeartBeat.startHeartBeat();
            }
            // Listen for heart beats from other servers
            redisHeartBeat.listenForHeartBeats();
            logger.info("Redis Heart Beat success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Heart Beat Fail! Plugin will be disabled!");
        }
    }

    private void initalizeRedisOperation() {
        logger.info("Start connecting to Redis Operation now...");
        try {
            redisOpeartion = new RedisOperation(this, config, mvWorldManager, redisHandler, cacheHandler);
            logger.info("Redis Operation success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Operation Fail! Plugin will be disabled!");
        }
    }

    private void initalizeRedisSubscribeRequest() {
        logger.info("Start connecting to Redis Subscribe Request now...");
        try {
            redisSubscribeRequest = new RedisSubscribeRequest(logger, config, redisHandler, redisOpeartion);
            redisSubscribeRequest.subscribeToRequests();
            logger.info("Redis Subscribe Request success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Subscribe Request Fail! Plugin will be disabled!");
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

    private void initializeMVWorldManager() {
        logger.info("Starting MVmanager");
        try {
            mvWorldManager = ((MultiverseCore) Objects.requireNonNull(Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core"))).getMVWorldManager();
            logger.info("MVmanager loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("MVmanager load fail! Plugin will be disabled!");
        }
    }

    private void initializeIslandHandler() {
        logger.info("Starting island handler");
        try {
            islandHandler = new IslandHandler(logger, config, redisHandler, redisHeartBeat, redisOpeartion);
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
        Objects.requireNonNull(this.getCommand("islandadmin")).setExecutor(new AdminCommandExecutor(this, config, cacheHandler, islandHandler));
        Objects.requireNonNull(this.getCommand("island")).setExecutor(new IslandCommandExecutor(config, cacheHandler, islandHandler));
    }

    @Override
    public void onDisable() {
        logger.info("Plugin disabling...");
        shutdown();
        logger.info("Plugin disabled!");
    }

    public void shutdown() {
        redisSubscribeRequest.unsubscribeFromRequests();
        redisHeartBeat.stopHeartBeat();
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
