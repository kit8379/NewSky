package org.me.newsky;

import co.aikar.commands.PaperCommandManager;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.IslandAdminCommand;
import org.me.newsky.command.IslandCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.*;
import org.me.newsky.island.middleware.PostIslandHandler;
import org.me.newsky.island.middleware.PreIslandHandler;
import org.me.newsky.listener.*;
import org.me.newsky.network.RedisPublishRequest;
import org.me.newsky.network.RedisSubscribeRequest;
import org.me.newsky.placeholder.NewSkyPlaceholderExpansion;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.scheduler.IslandUnloadScheduler;
import org.me.newsky.scheduler.LevelUpdateScheduler;
import org.me.newsky.teleport.TeleportHandler;
import org.me.newsky.thread.BukkitAsyncExecutor;
import org.me.newsky.world.WorldHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public class NewSky extends JavaPlugin {


    private PaperCommandManager commandManager;
    private ConfigHandler config;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private TeleportHandler teleportHandler;
    private HeartBeatHandler heartBeatHandler;
    private IslandUnloadScheduler islandUnloadScheduler;
    private LevelUpdateScheduler levelUpdateScheduler;
    private RedisPublishRequest brokerRequestPublish;
    private RedisSubscribeRequest brokerRequestSubscribe;
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
            info("Server ID load success!");
            info("This Server ID: " + serverID);

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

            info("Starting handlers for remote requests");
            PostIslandHandler postIslandHandler = new PostIslandHandler(this, cacheHandler, worldHandler, teleportHandler, serverID);
            brokerRequestSubscribe = new RedisSubscribeRequest(this, redisHandler, serverID, postIslandHandler);
            brokerRequestPublish = new RedisPublishRequest(this, redisHandler, serverID);
            PreIslandHandler preIslandHandler = new PreIslandHandler(this, cacheHandler, brokerRequestPublish, postIslandHandler, serverID);
            info("All handlers for remote requests loaded");

            info("Starting main handlers for the plugin");
            IslandHandler islandHandler = new IslandHandler(this, config, cacheHandler, preIslandHandler);
            PlayerHandler playerHandler = new PlayerHandler(this, cacheHandler);
            HomeHandler homeHandler = new HomeHandler(this, cacheHandler, preIslandHandler);
            WarpHandler warpHandler = new WarpHandler(this, cacheHandler, preIslandHandler);
            LevelHandler levelHandler = new LevelHandler(this, config, cacheHandler);
            BanHandler banHandler = new BanHandler(this, cacheHandler, preIslandHandler);
            info("All main handlers loaded");

            info("Starting plugin messaging");
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            info("Plugin messaging loaded");

            info("Starting all schedulers for the plugin");
            islandUnloadScheduler = new IslandUnloadScheduler(this, config, cacheHandler, worldHandler);
            levelUpdateScheduler = new LevelUpdateScheduler(this, config, levelHandler);
            info("All schedulers loaded");

            info("Starting API");
            api = new NewSkyAPI(this, islandHandler, playerHandler, homeHandler, warpHandler, levelHandler, banHandler);
            info("API loaded");

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
            islandUnloadScheduler.start();
            levelUpdateScheduler.start();
            brokerRequestPublish.subscribeToResponseChannel();
            brokerRequestSubscribe.subscribeToRequestChannel();

        } catch (Exception e) {
            warning("Plugin initialization failed!");
            warning(e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new OnlinePlayersListener(this, cacheHandler, serverID), this);
        getServer().getPluginManager().registerEvents(new WorldInitListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportRequestListener(this, teleportHandler), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(this, config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandMoveListener(this, config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandPvPListener(this, config, cacheHandler), this);
    }

    private void registerCommands() throws IOException, InvalidConfigurationException {
        commandManager = new PaperCommandManager(this);
        commandManager.enableUnstableAPI("help");
        commandManager.getLocales().setDefaultLocale(Locale.ENGLISH);
        commandManager.getLocales().loadYamlLanguageFile("messages.yml", Locale.ENGLISH);

        // Register the IslandCommand
        IslandCommand islandCommand = new IslandCommand(this, config, api);
        IslandAdminCommand islandAdminCommand = new IslandAdminCommand(this, config, api);
        commandManager.registerCommand(islandCommand);
        commandManager.registerCommand(islandAdminCommand);

        // Setup command completions
        setupCommandCompletions();
    }

    private void setupCommandCompletions() {
        // Register a dynamic completion for global online player names
        commandManager.getCommandCompletions().registerAsyncCompletion("globalplayers", context -> {
            try {
                return api.getOnlinePlayers();
            } catch (Exception e) {
                return Collections.emptyList();
            }
        });

        // Register a dynamic completion for home names
        commandManager.getCommandCompletions().registerAsyncCompletion("homes", context -> {
            Player player = context.getPlayer();
            if (player != null) {
                try {
                    return api.getHomeNames(player.getUniqueId()).join();
                } catch (Exception e) {
                    return Collections.emptyList();
                }
            }
            return Collections.emptyList();
        });

        // Register a dynamic completion for warp names
        commandManager.getCommandCompletions().registerAsyncCompletion("warps", context -> {
            Player player = context.getPlayer();
            if (player != null) {
                try {
                    return api.getWarpNames(player.getUniqueId()).join();
                } catch (Exception e) {
                    return Collections.emptyList();
                }
            }
            return Collections.emptyList();
        });
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
        islandUnloadScheduler.stop();
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
            info("DEBUG: " + message);
        }
    }
}