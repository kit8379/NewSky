package org.me.newsky;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.broker.CacheBroker;
import org.me.newsky.broker.IslandBroker;
import org.me.newsky.cache.Cache;
import org.me.newsky.command.IslandAdminCommand;
import org.me.newsky.command.IslandPlayerCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.*;
import org.me.newsky.island.distributor.IslandDistributor;
import org.me.newsky.island.operation.IslandOperation;
import org.me.newsky.listener.*;
import org.me.newsky.placeholder.NewSkyPlaceholderExpansion;
import org.me.newsky.redis.RedisCache;
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

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;

public class NewSky extends JavaPlugin {

    private ConfigHandler config;
    private WorldHandler worldHandler;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private Cache cache;
    private RedisCache redisCache;
    private TeleportHandler teleportHandler;
    private HeartBeatHandler heartBeatHandler;
    private IslandUnloadScheduler islandUnloadScheduler;
    private LevelUpdateScheduler levelUpdateScheduler;
    private MSPTUpdateScheduler msptUpdateScheduler;
    private CacheBroker cacheBroker;
    private IslandBroker islandBroker;
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
            info("Start loading configuration now...");
            saveDefaultConfig();
            config = new ConfigHandler(this);
            info("Config load success!");

            // Initialize the executor service
            info("Starting async executor");
            bukkitAsyncExecutor = new BukkitAsyncExecutor(this);
            info("Async executor started");

            info("Start loading server ID now...");
            serverID = config.getServerName();
            info("Server ID loaded success!");
            info("This Server ID: " + serverID);

            info("Starting World handler");
            worldHandler = new WorldHandler(this, config, teleportHandler);
            info("World handler loaded");

            info("Start connecting to Redis now...");
            redisHandler = new RedisHandler(this, config);
            info("Redis connection success!");

            info("Start connecting to Database now...");
            databaseHandler = new DatabaseHandler(this, config);
            info("Database connection success!");

            info("Starting cache handler");
            cache = new Cache(databaseHandler);
            info("Cache handler loaded");

            info("Starting Redis cache");
            redisCache = new RedisCache(this, redisHandler);
            info("Redis cache loaded");

            info("Starting teleport handler");
            teleportHandler = new TeleportHandler();
            info("Teleport handler loaded");

            info("Start connecting to Heart Beat system now...");
            heartBeatHandler = new HeartBeatHandler(this, config, redisCache, serverID);
            info("Heart Beat started!");

            info("Starting server selector");
            ServerSelector serverSelector;
            switch (config.getServerSelector().toLowerCase()) {
                case "round-robin":
                    serverSelector = new RoundRobinServerSelector(redisCache);
                    info("Using Round Robin server selector");
                    break;
                case "mspt":
                    serverSelector = new MSPTServerSelector(redisCache);
                    info("Using MSPT server selector");
                    break;
                case "random":
                default:
                    serverSelector = new RandomServerSelector();
                    info("Using Random server selector");
            }
            info("Server selector loaded");

            info("Starting handlers for island remote requests");
            IslandOperation islandOperation = new IslandOperation(this, config, cache, redisCache, worldHandler, teleportHandler, serverID);
            IslandDistributor islandDistributor = new IslandDistributor(this, redisCache, islandOperation, serverSelector, serverID);
            info("All handlers for remote requests loaded");

            info("Starting message broker");
            cacheBroker = new CacheBroker(this, redisHandler, cache, serverID, config.getRedisCacheChannel());
            cache.setCacheBroker(cacheBroker);
            islandBroker = new IslandBroker(this, redisHandler, islandOperation, serverID, config.getRedisIslandChannel());
            islandDistributor.setIslandBroker(islandBroker);
            info("Message broker loaded");

            info("Starting main handlers for the plugin");
            IslandHandler islandHandler = new IslandHandler(this, cache, islandDistributor);
            PlayerHandler playerHandler = new PlayerHandler(this, config, cache, redisCache);
            HomeHandler homeHandler = new HomeHandler(this, cache, islandDistributor);
            WarpHandler warpHandler = new WarpHandler(this, cache, islandDistributor);
            LevelHandler levelHandler = new LevelHandler(config, cache);
            BanHandler banHandler = new BanHandler(this, cache, islandDistributor);
            CoopHandler coopHandler = new CoopHandler(this, cache);
            info("All main handlers loaded");

            info("Starting plugin messaging");
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            info("Plugin messaging loaded");

            info("Starting all schedulers for the plugin");
            islandUnloadScheduler = new IslandUnloadScheduler(this, config, redisCache, worldHandler);
            levelUpdateScheduler = new LevelUpdateScheduler(this, config, levelHandler);
            msptUpdateScheduler = new MSPTUpdateScheduler(this, config, redisCache, serverID);
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
                new NewSkyPlaceholderExpansion(this, cache).register();
                info("Placeholder registered");
            } else {
                info("PlaceholderAPI not found, skipping placeholder registration");
            }

            databaseHandler.createTables();
            cacheBroker.subscribe();
            islandBroker.subscribe();
            cache.cacheAllData();
            heartBeatHandler.start();
            islandUnloadScheduler.start();
            levelUpdateScheduler.start();
            msptUpdateScheduler.start();


        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred during plugin initialization", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new OnlinePlayersListener(this, redisCache, serverID), this);
        getServer().getPluginManager().registerEvents(new WorldInitListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportRequestListener(this, teleportHandler), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this, config, cache), this);
        getServer().getPluginManager().registerEvents(new IslandBoundaryListener(this, config), this);
        getServer().getPluginManager().registerEvents(new IslandAccessListener(this, config, cache), this);
        getServer().getPluginManager().registerEvents(new IslandPvPListener(this, config, cache), this);
    }

    private void registerCommands() {
        // /island and alias /is
        PluginCommand islandCommand = createCommand("island");
        islandCommand.setAliases(Collections.singletonList("is"));
        islandCommand.setExecutor(new IslandPlayerCommand(this, api, config));
        islandCommand.setTabCompleter(new IslandPlayerCommand(this, api, config));
        Bukkit.getCommandMap().register("island", islandCommand);

        // /islandadmin and alias /isadmin
        PluginCommand adminCommand = createCommand("islandadmin");
        adminCommand.setAliases(Collections.singletonList("isadmin"));
        adminCommand.setExecutor(new IslandAdminCommand(this, api, config));
        adminCommand.setTabCompleter(new IslandAdminCommand(this, api, config));
        Bukkit.getCommandMap().register("islandadmin", adminCommand);
    }

    private PluginCommand createCommand(String name) {
        try {
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);
            return c.newInstance(name, this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create command: " + name, e);
        }
    }

    @Override
    public void onDisable() {
        info("Plugin disabling...");
        shutdown();
        info("Plugin disabled!");
    }

    public void shutdown() {
        levelUpdateScheduler.stop();
        islandUnloadScheduler.stop();
        msptUpdateScheduler.stop();
        worldHandler.unloadAllWorldsOnShutdown();
        heartBeatHandler.stop();
        islandBroker.unsubscribe();
        cacheBroker.unsubscribe();
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
        return redisCache.getOnlinePlayers();
    }

    public BukkitAsyncExecutor getBukkitAsyncExecutor() {
        return bukkitAsyncExecutor;
    }

    @SuppressWarnings("unused")
    public NewSkyAPI getApi() {
        return api;
    }

    public void info(String message) {
        getLogger().log(Level.INFO, message);
    }

    public void severe(String message) {
        getLogger().log(Level.SEVERE, message);
    }

    public void severe(String message, Throwable throwable) {
        getLogger().log(Level.SEVERE, message, throwable);
    }

    public void debug(String source, String message) {
        if (config.isDebug()) {
            getLogger().log(Level.INFO, "[" + source + "] " + message);
        }
    }
}