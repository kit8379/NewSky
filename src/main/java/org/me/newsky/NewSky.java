package org.me.newsky;

import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.AdminCommandExecutor;
import org.me.newsky.command.PlayerCommandExecutor;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.event.*;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.PostIslandHandler;
import org.me.newsky.island.PreIslandHandler;
import org.me.newsky.redis.RedisBroker;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.util.Objects;

public class NewSky extends JavaPlugin {

    private ConfigHandler config;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private TeleportManager teleportManager;
    private HeartBeatHandler heartBeatHandler;
    private RedisBroker broker;
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
            WorldHandler worldHandler = new WorldHandler(this, config);
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
            teleportManager = new TeleportManager(this);
            info("Teleport manager loaded");

            info("Start connecting to Heart Beat system now...");
            heartBeatHandler = new HeartBeatHandler(this, config, cacheHandler, serverID);
            info("Heart Beat started!");

            info("Starting island handler");
            PostIslandHandler postIslandHandler = new PostIslandHandler(this, cacheHandler, worldHandler, teleportManager);
            info("Post island handler loaded");

            info("Starting broker");
            broker = new RedisBroker(this, config, redisHandler, postIslandHandler);
            info("Broker loaded");

            info("Starting pre island handler");
            PreIslandHandler preIslandHandler = new PreIslandHandler(this, cacheHandler, broker, postIslandHandler, serverID);
            info("Pre island handler loaded");

            info("Starting API");
            api = new NewSkyAPI(config, cacheHandler, preIslandHandler);
            info("API loaded");

            registerListeners();
            registerCommands();

            databaseHandler.createTables();
            cacheHandler.cacheAllDataToRedis();
            broker.subscribe();
            heartBeatHandler.start();

        } catch (Exception e) {
            e.printStackTrace();
            info("Plugin initialization failed!");
            shutdown();
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WorldInitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, teleportManager), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandMoveListener(config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandPvPListener(config, cacheHandler), this);
    }

    private void registerCommands() {
        Objects.requireNonNull(this.getCommand("islandadmin")).setExecutor(new AdminCommandExecutor(this, config, api));
        Objects.requireNonNull(this.getCommand("island")).setExecutor(new PlayerCommandExecutor(config, api));
    }

    @Override
    public void onDisable() {
        info("Plugin disabling...");
        shutdown();
        info("Plugin disabled!");
    }

    public void shutdown() {
        heartBeatHandler.stop();
        broker.unsubscribe();
        redisHandler.disconnect();
        databaseHandler.close();
    }

    public void reload() {
        info("Plugin reloading...");
        shutdown();
        initialize();
        info("Plugin reloaded!");
    }

    public void debug(String message) {
        if (config.isDebug()) {
            info(config.getDebugPrefix() + message);
        }
    }

    public void info(String message) {
        getLogger().info(message);
    }
}