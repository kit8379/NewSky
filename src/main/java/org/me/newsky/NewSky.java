package org.me.newsky;

import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.IslandAdminCommand;
import org.me.newsky.command.IslandPlayerCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.*;
import org.me.newsky.island.operation.LocalIslandOperation;
import org.me.newsky.island.middleware.IslandServiceDistributor;
import org.me.newsky.listener.*;
import org.me.newsky.network.Broker;
import org.me.newsky.placeholder.NewSkyPlaceholderExpansion;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.routing.MSPTServerSelector;
import org.me.newsky.routing.RandomServerSelector;
import org.me.newsky.routing.RoundRobinServerSelector;
import org.me.newsky.routing.ServerSelector;
import org.me.newsky.scheduler.IslandUnloadScheduler;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.scheduler.MSPTUpdateScheduler;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.thread.BukkitAsyncExecutor;
import org.me.newsky.world.WorldHandler;

import java.util.*;
import java.util.logging.Level;

public class NewSky extends JavaPlugin {

    private ConfigHandler config;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private TeleportHandler teleportHandler;
    private HeartBeatHandler heartBeatHandler;
    private IslandUnloadScheduler islandUnloadScheduler;
    private LevelUpdateScheduler levelUpdateScheduler;
    private MSPTUpdateScheduler msptUpdateScheduler;
    private Broker broker;
    private NewSkyAPI api;
    private BukkitAsyncExecutor bukkitAsyncExecutor;
    private String serverID;


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
            // Initialize the executor service
            info("Starting async executor");
            bukkitAsyncExecutor = new BukkitAsyncExecutor(this);
            info("Async executor started");

            info("Start loading configuration now...");
            saveDefaultConfig();
            config = new ConfigHandler(this);
            info("Config load success!");

            info("Start loading server ID now...");
            serverID = config.getServerName();
            info("Server ID loaded success!");
            info("This Server ID: " + serverID);

            info("Start loading Redis Pub/Sub channel name now...");
            String channelID = config.getRedisChannel();
            info("Redis Pub/Sub channel name loaded success!");
            info("This server using Redis Pub/Sub channel name: " + channelID);

            info("Starting World handler");
            WorldHandler worldHandler = new WorldHandler(this, config, teleportHandler);
            info("World handler loaded");

            info("Start connecting to Redis now...");
            redisHandler = new RedisHandler(this, config);
            info("Redis connection success!");

            info("Start connecting to Database now...");
            databaseHandler = new DatabaseHandler(config);
            info("Database connection success!");

            info("Starting cache handler");
            cacheHandler = new CacheHandler(this, redisHandler, databaseHandler);
            info("Cache handler loaded");

            info("Starting teleport handler");
            teleportHandler = new TeleportHandler();
            info("Teleport handler loaded");

            info("Start connecting to Heart Beat system now...");
            heartBeatHandler = new HeartBeatHandler(this, config, cacheHandler, serverID);
            info("Heart Beat started!");

            info("Starting server selector");
            ServerSelector serverSelector;
            switch (config.getServerSelector().toLowerCase()) {
                case "round-robin":
                    serverSelector = new RoundRobinServerSelector(cacheHandler);
                    info("Using Round Robin server selector");
                    break;
                case "mspt":
                    serverSelector = new MSPTServerSelector(cacheHandler);
                    info("Using MSPT server selector");
                    break;
                case "random":
                default:
                    serverSelector = new RandomServerSelector();
                    info("Using Random server selector");
            }
            info("Server selector loaded");

            info("Starting handlers for remote requests");
            LocalIslandOperation localIslandOperation = new LocalIslandOperation(this, config, cacheHandler, worldHandler, teleportHandler, serverID);
            broker = new Broker(this, redisHandler, localIslandOperation, serverID, channelID);
            IslandServiceDistributor islandServiceDistributor = new IslandServiceDistributor(this, cacheHandler, broker, localIslandOperation, serverSelector, serverID);
            info("All handlers for remote requests loaded");

            info("Starting main handlers for the plugin");
            IslandHandler islandHandler = new IslandHandler(this, config, cacheHandler, islandServiceDistributor);
            PlayerHandler playerHandler = new PlayerHandler(this, cacheHandler);
            HomeHandler homeHandler = new HomeHandler(this, cacheHandler, islandServiceDistributor);
            WarpHandler warpHandler = new WarpHandler(this, cacheHandler, islandServiceDistributor);
            LevelHandler levelHandler = new LevelHandler(this, config, cacheHandler);
            BanHandler banHandler = new BanHandler(this, cacheHandler, islandServiceDistributor);
            CoopHandler coopHandler = new CoopHandler(this, cacheHandler);
            info("All main handlers loaded");

            info("Starting plugin messaging");
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            info("Plugin messaging loaded");

            info("Starting all schedulers for the plugin");
            islandUnloadScheduler = new IslandUnloadScheduler(this, config, cacheHandler, worldHandler);
            levelUpdateScheduler = new LevelUpdateScheduler(this, config, levelHandler);
            msptUpdateScheduler = new MSPTUpdateScheduler(this, config, cacheHandler, serverID);
            info("All schedulers loaded");

            info("Starting API");
            api = new NewSkyAPI(this, islandHandler, playerHandler, homeHandler, warpHandler, levelHandler, banHandler, coopHandler);
            info("API loaded");

            info("Starting listeners and commands");
            registerListeners();
            registerCommands();
            info("Listeners and commands loaded");

            info("Registering placeholder");
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                info("PlaceholderAPI found, registering placeholders");
                new NewSkyPlaceholderExpansion(this, cacheHandler).register();
                info("Placeholder registered");
            } else {
                info("PlaceholderAPI not found, skipping placeholder registration");
            }

            databaseHandler.createTables();
            cacheHandler.cacheAllDataToRedis();
            heartBeatHandler.start();
            islandUnloadScheduler.start();
            levelUpdateScheduler.start();
            msptUpdateScheduler.start();
            broker.subscribe();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred during plugin initialization", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new OnlinePlayersListener(this, cacheHandler, serverID), this);
        getServer().getPluginManager().registerEvents(new WorldInitListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportRequestListener(this, teleportHandler), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this, config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandBoundaryListener(this, config), this);
        getServer().getPluginManager().registerEvents(new IslandBanListener(this, config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandLockListener(this, config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandPvPListener(this, config, cacheHandler), this);
    }

    private void registerCommands() {
        // Create and register the /is (player) command
        IslandPlayerCommand playerExecutor = new IslandPlayerCommand(this, api, config);
        Objects.requireNonNull(getCommand("island")).setExecutor(playerExecutor);
        Objects.requireNonNull(getCommand("island")).setAsyncTabCompleter(playerExecutor);

        // Create and register the /isadmin (admin) command
        IslandAdminCommand adminExecutor = new IslandAdminCommand(this, api, config);
        Objects.requireNonNull(getCommand("islandadmin")).setExecutor(adminExecutor);
        Objects.requireNonNull(getCommand("islandadmin")).setAsyncTabCompleter(adminExecutor);
    }

    @Override
    public void onDisable() {
        info("Plugin disabling...");
        shutdown();
        info("Plugin disabled!");
    }

    public void shutdown() {
        broker.unsubscribe();
        levelUpdateScheduler.stop();
        islandUnloadScheduler.stop();
        msptUpdateScheduler.stop();
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

    public Set<String> getOnlinePlayers() {
        return cacheHandler.getOnlinePlayers();
    }

    public BukkitAsyncExecutor getBukkitAsyncExecutor() {
        return bukkitAsyncExecutor;
    }

    @SuppressWarnings("unused")
    public NewSkyAPI getApi() {
        return api;
    }

    @SuppressWarnings("unused")
    public void info(String message) {
        getLogger().info(message);
    }

    @SuppressWarnings("unused")
    public void debug(String module, String message) {
        if (config.isDebug()) {
            info(String.format("[DEBUG] [%s] %s", module, message));
        }
    }
}