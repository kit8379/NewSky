package org.me.newsky;

import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.AdminCommandExecutor;
import org.me.newsky.command.PlayerCommandExecutor;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.*;
import org.me.newsky.island.middleware.PostIslandHandler;
import org.me.newsky.island.middleware.PreIslandHandler;
import org.me.newsky.listener.*;
import org.me.newsky.network.BasePublishRequest;
import org.me.newsky.network.BaseSubscribeRequest;
import org.me.newsky.network.redis.RedisPublishRequest;
import org.me.newsky.network.redis.RedisSubscribeRequest;
import org.me.newsky.placeholder.NewSkyPlaceholderExpansion;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.scheduler.WorldUnloadScheduler;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.world.WorldHandler;

import java.io.*;
import java.util.Objects;

public class NewSky extends JavaPlugin {

    private ConfigHandler config;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private TeleportHandler teleportHandler;
    private HeartBeatHandler heartBeatHandler;
    private WorldUnloadScheduler worldUnloadScheduler;
    private LevelUpdateScheduler levelUpdateScheduler;
    private BasePublishRequest brokerRequestPublish;
    private BaseSubscribeRequest brokerRequestSubscribe;
    private NewSkyAPI api;

    @Override
    public void onEnable() {
        // Calculate the time it takes to initialize the plugin
        long startTime = System.currentTimeMillis();
        info("Plugin enabling...");
        initialize();
        info("Plugin enabled!");
        long endTime = System.currentTimeMillis();
        info("Plugin initialization time: " + (endTime - startTime) + "ms");
    }

    private void initialize() {
        try {
            info("Start loading configuration now...");
            saveDefaultConfig();
            config = new ConfigHandler(this);
            info("Config load success!");

            info("Start loading server ID now...");
            String serverID = config.getServerName();
            info("Server ID load success!");
            info("This Server ID: " + serverID);

            info("Starting WorldHandler");
            WorldHandler worldHandler = new WorldHandler(this, config, teleportHandler);
            info("WorldHandler loaded");

            info("Start connecting to Redis now...");
            redisHandler = new RedisHandler(this, config);
            info("Redis connection success!");

            info("Start connecting to Database now...");
            databaseHandler = new DatabaseHandler(config);
            info("Database connection success!");

            info("Starting cache handler");
            cacheHandler = new CacheHandler(redisHandler, databaseHandler);
            info("Cache to Redis success");

            info("Starting teleport manager");
            teleportHandler = new TeleportHandler();
            info("Teleport manager loaded");

            info("Start connecting to Heart Beat system now...");
            heartBeatHandler = new HeartBeatHandler(this, config, cacheHandler, serverID);
            info("Heart Beat started!");

            info("Starting handlers for remote requests");
            PostIslandHandler postIslandHandler = new PostIslandHandler(this, cacheHandler, worldHandler, teleportHandler, serverID);
            brokerRequestSubscribe = new RedisSubscribeRequest(this, redisHandler, serverID, postIslandHandler);
            brokerRequestPublish = new RedisPublishRequest(this, redisHandler, serverID);
            PreIslandHandler preIslandHandler = new PreIslandHandler(this, cacheHandler, brokerRequestPublish, postIslandHandler, serverID);
            info("All handlers for remote requests loaded");

            info("Starting main handlers for the plugin");
            IslandHandler islandHandler = new IslandHandler(config, cacheHandler, preIslandHandler);
            PlayerHandler playerHandler = new PlayerHandler(cacheHandler);
            HomeHandler homeHandler = new HomeHandler(config, cacheHandler, preIslandHandler);
            WarpHandler warpHandler = new WarpHandler(config, cacheHandler, preIslandHandler);
            LevelHandler levelHandler = new LevelHandler(config, cacheHandler);
            info("All main handlers loaded");

            info("Starting plugin messaging");
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            info("Plugin messaging loaded");

            info("Starting API");
            api = new NewSkyAPI(islandHandler, playerHandler, homeHandler, warpHandler, levelHandler);
            info("API loaded");

            info("Starting all schedulers for the plugin");
            worldUnloadScheduler = new WorldUnloadScheduler(this, config, cacheHandler, worldHandler);
            levelUpdateScheduler = new LevelUpdateScheduler(this, config, levelHandler);
            info("All schedulers loaded");

            info("Starting listeners and commands");
            registerListeners();
            registerCommands();
            info("Listeners and commands loaded");

            info("Registering placeholder");
            registerPlaceholder();
            info("Placeholder registered");

            databaseHandler.createTables();
            cacheHandler.cacheAllDataToRedis();
            heartBeatHandler.start();
            worldUnloadScheduler.start();
            levelUpdateScheduler.start();
            brokerRequestPublish.subscribeToResponseChannel();
            brokerRequestSubscribe.subscribeToRequestChannel();

        } catch (Exception e) {
            e.printStackTrace();
            info("Plugin initialization failed!");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WorldInitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, teleportHandler), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandMoveListener(config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandPvPListener(config, cacheHandler), this);
    }

    private void registerCommands() {
        Objects.requireNonNull(this.getCommand("islandadmin")).setExecutor(new AdminCommandExecutor(this, config, api));
        Objects.requireNonNull(this.getCommand("island")).setExecutor(new PlayerCommandExecutor(config, api));
    }

    private void registerPlaceholder() {
        new NewSkyPlaceholderExpansion(this, cacheHandler).register();
    }

    @Override
    public void onDisable() {
        info("Plugin disabling...");
        shutdown();
        info("Plugin disabled!");
    }

    public void shutdown() {
        brokerRequestSubscribe.unsubscribeFromRequestChannel();
        brokerRequestPublish.unsubscribeFromResponseChannel();
        levelUpdateScheduler.stop();
        worldUnloadScheduler.stop();
        heartBeatHandler.stop();
        redisHandler.disconnect();
        databaseHandler.close();
    }

    public void reload() {
        info("Plugin reloading...");
        shutdown();
        initialize();
        info("Plugin reloaded!");
    }

    @SuppressWarnings("unused")
    public void info(String message) {
        getLogger().info(message);
    }

    @SuppressWarnings("unused")
    public void warning(String message) {
        getLogger().warning(message);
    }

    @SuppressWarnings("unused")
    public void severe(String message) {
        getLogger().severe(message);
    }

    @SuppressWarnings("unused")
    public void debug(String message) {
        if (config.isDebug()) {
            info(config.getDebugPrefix() + message);
        }
    }
}