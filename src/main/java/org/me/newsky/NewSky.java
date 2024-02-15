package org.me.newsky;

import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.admin.AdminCommandExecutor;
import org.me.newsky.command.player.IslandCommandExecutor;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.event.*;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.DynamicIslandHandler;
import org.me.newsky.island.IslandHandler;
import org.me.newsky.island.StaticIslandHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.scheduler.WorldUnloadSchedule;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.DynamicWorldHandler;
import org.me.newsky.world.StaticWorldHandler;
import org.me.newsky.world.WorldHandler;

import java.nio.file.Path;
import java.util.Objects;

public class NewSky extends JavaPlugin {

    private String serverID;
    private ConfigHandler config;
    private WorldHandler worldHandler;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private TeleportManager teleportManager;
    private HeartBeatHandler heartBeatHandler;
    private WorldUnloadSchedule worldUnloadSchedule;
    private IslandHandler islandHandler;

    @Override
    public void onEnable() {
        info("Plugin enabling...");
        initalize();
        info("Plugin enabled!");
    }

    private void initalize() {
        initializeConfig();
        initalizeServerID();
        initializeWorldHandler();
        initializeRedis();
        initializeDatabase();
        initializeCache();
        initializeTeleportManager();
        initalizeheartBeatHandler();
        initalizeWorldUnloadSchedule();
        initalizePluginMessaging();
        initializeIslandHandler();
        registerListeners();
        registerCommands();
    }

    private void initializeConfig() {
        info("Start loading configuration now...");
        try {
            saveDefaultConfig();
            config = new ConfigHandler(getConfig());
            info("Config load success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Config load fail! Plugin will be disabled!");
        }
    }

    private void initalizeServerID() {
        info("Start loading server ID now...");
        try {
            serverID = config.getServerName();
            info("Server ID load success!");
            info("This Server ID: " + serverID);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Server ID load fail! Plugin will be disabled!");
        }
    }

    private void initializeWorldHandler() {
        info("Starting WorldHandler");
        try {
            if (config.getServerMode().equals("static")) {
                info("Using static world mode.");
                worldHandler = new StaticWorldHandler(this, config);
            }

            if (config.getServerMode().equals("dynamic")) {
                info("Using dynamic world mode. World storage path: " + config.getStoragePath() + " .");
                worldHandler = new DynamicWorldHandler(this, config, Path.of(config.getStoragePath()));
            }
            info("WorldHandler loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("WorldHandler load fail! Plugin will be disabled!");
        }
    }

    private void initializeRedis() {
        info("Start connecting to Redis now...");
        try {
            redisHandler = new RedisHandler(this, config);
            info("Redis connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Fail! Plugin will be disabled!");
        }
    }

    private void initializeDatabase() {
        info("Start connecting to Database now...");
        try {
            databaseHandler = new DatabaseHandler(config);
            databaseHandler.createTables();
            info("Database connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Database connection fail! Plugin will be disabled!");
        }
    }

    private void initializeCache() {
        info("Starting to cache into Redis");
        try {
            cacheHandler = new CacheHandler(redisHandler, databaseHandler);
            cacheHandler.cacheAllDataToRedis();
            info("Cache to Redis success");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Cache to Redis fail! Plugin will be disabled!");
        }
    }

    private void initializeTeleportManager() {
        info("Starting teleport manager");
        try {
            teleportManager = new TeleportManager(this);
            info("Teleport manager loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Teleport manager load fail! Plugin will be disabled!");
        }
    }

    private void initalizeheartBeatHandler() {
        info("Start connecting to Heart Beat system now...");
        try {
            heartBeatHandler = new HeartBeatHandler(this, config, redisHandler, serverID);
            heartBeatHandler.startHeartBeat();
            info("Heart Beat started!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Heart Beat Fail! Plugin will be disabled!");
        }
    }

    private void initalizeWorldUnloadSchedule() {
        info("Starting world unload handler");
        try {
            worldUnloadSchedule = new WorldUnloadSchedule(this, worldHandler);
            worldUnloadSchedule.startWorldUnloadTask();
            info("World unload handler loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("World unload schedule load fail! Plugin will be disabled!");
        }
    }

    private void initalizePluginMessaging() {
        info("Starting plugin messaging");
        try {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            info("Plugin messaging loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Plugin messaging load fail! Plugin will be disabled!");
        }
    }

    private void initializeIslandHandler() {
        info("Starting island handler");
        try {
            if (config.getServerMode().equals("static")) {
                islandHandler = new StaticIslandHandler(this, config, worldHandler, redisHandler, heartBeatHandler, teleportManager, serverID);
            }

            if (config.getServerMode().equals("dynamic")) {
                islandHandler = new DynamicIslandHandler(this, config, worldHandler, redisHandler, heartBeatHandler, teleportManager, serverID);
            }

            islandHandler.subscribeToRequests();
            info("Island handler loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Islands load fail! Plugin will be disabled!");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WorldEventListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinEventListener(this, teleportManager), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandBoundaryListener(config), this);
        getServer().getPluginManager().registerEvents(new IslandPvPListener(config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandLockListener(config, cacheHandler), this);
    }

    private void registerCommands() {
        Objects.requireNonNull(this.getCommand("islandadmin")).setExecutor(new AdminCommandExecutor(this, config, cacheHandler, islandHandler));
        Objects.requireNonNull(this.getCommand("island")).setExecutor(new IslandCommandExecutor(config, cacheHandler, islandHandler));
    }

    @Override
    public void onDisable() {
        info("Plugin disabling...");
        shutdown();
        info("Plugin disabled!");
    }

    public void shutdown() {
        islandHandler.unsubscribeFromRequests();
        heartBeatHandler.stopHeartBeat();
        worldUnloadSchedule.stopWorldUnloadTask();
        redisHandler.disconnect();
        databaseHandler.close();
    }

    public void reload() {
        info("Plugin reloading...");
        shutdown();
        initalize();
        info("Plugin reloaded!");
    }

    public void info(String message) {
        getLogger().info(message);
    }

    public void debug(String message) {
        if (config.isDebug()) {
            getLogger().info(config.getDebugPrefix() + message);
        }
    }
}
