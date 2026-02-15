package org.me.newsky;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.broker.CacheBroker;
import org.me.newsky.broker.IslandBroker;
import org.me.newsky.broker.PlayerMessageBroker;
import org.me.newsky.cache.Cache;
import org.me.newsky.command.IslandAdminCommand;
import org.me.newsky.command.IslandPlayerCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.*;
import org.me.newsky.listener.*;
import org.me.newsky.message.PlayerMessageHandler;
import org.me.newsky.network.distributor.IslandDistributor;
import org.me.newsky.network.lock.IslandOpLock;
import org.me.newsky.network.operator.IslandOperator;
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
import org.me.newsky.uuid.UuidHandler;
import org.me.newsky.world.WorldActivityHandler;
import org.me.newsky.world.WorldHandler;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class NewSky extends JavaPlugin {

    private ConfigHandler config;
    private WorldHandler worldHandler;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private RedisCache redisCache;
    private TeleportHandler teleportHandler;
    private HeartBeatHandler heartBeatHandler;
    private IslandUnloadScheduler islandUnloadScheduler;
    private LevelUpdateScheduler levelupdateScheduler;
    private MSPTUpdateScheduler msptUpdateScheduler;
    private CacheBroker cacheBroker;
    private IslandBroker islandBroker;
    private PlayerMessageBroker playerMessageBroker;
    private LevelHandler levelHandler;
    private CobblestoneGeneratorHandler cobblestoneGeneratorHandler;
    private NewSkyAPI api;
    private BukkitAsyncExecutor bukkitAsyncExecutor;


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
            String serverID = config.getServerName();
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
            Cache cache = new Cache(config, databaseHandler);
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
            switch (config.getServerSelector().toLowerCase(Locale.ROOT)) {
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
            IslandOpLock islandOpLock = new IslandOpLock(this, redisCache, serverID);
            IslandOperator islandOperator = new IslandOperator(this, redisCache, worldHandler, teleportHandler, serverID);
            IslandDistributor islandDistributor = new IslandDistributor(this, redisCache, islandOperator, serverSelector, islandOpLock, serverID);
            info("All handlers for remote requests loaded");

            info("Starting player message handler");
            PlayerMessageHandler playerMessageHandler = new PlayerMessageHandler(this, redisCache);
            info("Player message handler loaded");

            info("Starting all brokers for the plugin");
            cacheBroker = new CacheBroker(this, redisHandler, cache, serverID, config.getRedisCacheChannel());
            cache.setCacheBroker(cacheBroker);
            islandBroker = new IslandBroker(this, redisHandler, islandOperator, serverID, config.getRedisIslandChannel());
            islandDistributor.setIslandBroker(islandBroker);
            playerMessageBroker = new PlayerMessageBroker(this, redisHandler, serverID, config.getRedisPlayerMessageChannel());
            playerMessageHandler.setPlayerMessageBroker(playerMessageBroker);
            info("All brokers loaded");

            info("Starting main handlers for the plugin");
            IslandHandler islandHandler = new IslandHandler(this, cache, islandDistributor);
            PlayerHandler playerHandler = new PlayerHandler(this, cache, redisCache, islandDistributor);
            HomeHandler homeHandler = new HomeHandler(this, cache, islandDistributor);
            WarpHandler warpHandler = new WarpHandler(this, cache, islandDistributor);
            levelHandler = new LevelHandler(this, config, cache);
            BanHandler banHandler = new BanHandler(this, cache, islandDistributor);
            CoopHandler coopHandler = new CoopHandler(this, cache);
            UpgradeHandler upgradeHandler = new UpgradeHandler(this, config, cache, islandDistributor);
            cobblestoneGeneratorHandler = new CobblestoneGeneratorHandler(this, upgradeHandler);
            LobbyHandler lobbyHandler = new LobbyHandler(this, config, islandDistributor);
            UuidHandler uuidHandler = new UuidHandler(this, cache);
            WorldActivityHandler worldActivityHandler = new WorldActivityHandler(this);
            info("All main handlers loaded");

            info("Starting plugin messaging");
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            info("Plugin messaging loaded");

            info("Starting all schedulers for the plugin");
            islandUnloadScheduler = new IslandUnloadScheduler(this, config, redisCache, worldHandler, worldActivityHandler, islandOpLock);
            levelupdateScheduler = new LevelUpdateScheduler(this, levelHandler);
            if (serverSelector instanceof MSPTServerSelector) {
                info("MSPT server selector detected, creating MSPT update scheduler");
                msptUpdateScheduler = new MSPTUpdateScheduler(this, config, redisCache, serverID);
            } else {
                msptUpdateScheduler = null;
            }
            info("All schedulers loaded");

            info("Starting API");
            api = new NewSkyAPI(this, islandHandler, playerHandler, homeHandler, warpHandler, levelHandler, banHandler, coopHandler, lobbyHandler, playerMessageHandler, uuidHandler, upgradeHandler);
            info("API loaded");

            info("Starting listeners");
            getServer().getPluginManager().registerEvents(new OnlinePlayersListener(this, redisCache, serverID), this);
            getServer().getPluginManager().registerEvents(new WorldInitListener(this), this);
            getServer().getPluginManager().registerEvents(new WorldLoadListener(this, config, levelupdateScheduler), this);
            getServer().getPluginManager().registerEvents(new WorldUnloadListener(this, levelupdateScheduler), this);
            getServer().getPluginManager().registerEvents(new WorldActivityListener(this, worldActivityHandler), this);
            getServer().getPluginManager().registerEvents(new TeleportRequestListener(this, teleportHandler), this);
            getServer().getPluginManager().registerEvents(new IslandProtectionListener(this, config), this);
            getServer().getPluginManager().registerEvents(new IslandAccessListener(this, config), this);
            getServer().getPluginManager().registerEvents(new IslandPvPListener(this, config), this);
            getServer().getPluginManager().registerEvents(new UuidUpdateListener(this), this);
            getServer().getPluginManager().registerEvents(new CobblestoneGeneratorListener(this, cobblestoneGeneratorHandler), this);
            info("All listeners loaded");

            info("Registering commands");
            PluginCommand islandCommand = createCommand("island");
            islandCommand.setAliases(Collections.singletonList("is"));
            islandCommand.setExecutor(new IslandPlayerCommand(this, api, config));
            islandCommand.setTabCompleter(new IslandPlayerCommand(this, api, config));
            Bukkit.getCommandMap().register("island", islandCommand);

            PluginCommand adminCommand = createCommand("islandadmin");
            adminCommand.setAliases(Collections.singletonList("isadmin"));
            adminCommand.setExecutor(new IslandAdminCommand(this, api, config));
            adminCommand.setTabCompleter(new IslandAdminCommand(this, api, config));
            Bukkit.getCommandMap().register("islandadmin", adminCommand);
            info("All commands registered");

            info("Registering placeholder");
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                info("PlaceholderAPI found, registering placeholders");
                new NewSkyPlaceholderExpansion(this).register();
                info("Placeholder registered");
            } else {
                info("PlaceholderAPI not found, skipping placeholder registration");
            }

            databaseHandler.createTables();
            cacheBroker.subscribe();
            islandBroker.subscribe();
            playerMessageBroker.subscribe();
            cache.cacheAllData();
            heartBeatHandler.start();
            islandUnloadScheduler.start();
            levelupdateScheduler.start();

            if (msptUpdateScheduler != null) {
                msptUpdateScheduler.start();
            } else {
                info("MSPT update scheduler not enabled (server selector is not MSPT), skipping start.");
            }

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred during plugin initialization", e);
            getServer().getPluginManager().disablePlugin(this);
        }
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
        if (msptUpdateScheduler != null) {
            msptUpdateScheduler.stop();
        }
        levelupdateScheduler.stop();
        islandUnloadScheduler.stop();
        worldHandler.unloadAllWorldsOnShutdown();
        heartBeatHandler.stop();
        playerMessageBroker.unsubscribe();
        islandBroker.unsubscribe();
        cacheBroker.unsubscribe();
        redisHandler.disconnect();
        databaseHandler.close();
    }

    public void reload() {
        info("Plugin configs reloading...");
        config.reload();
        levelHandler.reload();
        cobblestoneGeneratorHandler.reload();
        info("Plugin configs reloaded!");
    }

    @SuppressWarnings("unused")
    public Set<UUID> getOnlinePlayersUUIDs() {
        return redisCache.getOnlinePlayersUUIDs();
    }

    @SuppressWarnings("unused")
    public Set<String> getOnlinePlayersNames() {
        return redisCache.getOnlinePlayersNames();
    }

    @SuppressWarnings("unused")
    public BukkitAsyncExecutor getBukkitAsyncExecutor() {
        return bukkitAsyncExecutor;
    }


    @SuppressWarnings("unused")
    public NewSkyAPI getApi() {
        return api;
    }

    @SuppressWarnings("unused")
    public void info(String message) {
        getLogger().log(Level.INFO, message);
    }

    @SuppressWarnings("unused")
    public void warning(String message) {
        getLogger().log(Level.WARNING, message);
    }

    @SuppressWarnings("unused")
    public void severe(String message) {
        getLogger().log(Level.SEVERE, message);
    }

    @SuppressWarnings("unused")
    public void severe(String message, Throwable throwable) {
        getLogger().log(Level.SEVERE, message, throwable);
    }

    @SuppressWarnings("unused")
    public void debug(String source, String message) {
        if (config.isDebug()) {
            getLogger().log(Level.INFO, "[" + source + "] " + message);
        }
    }
}