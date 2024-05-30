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
import org.me.newsky.util.ColorUtils;
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

            info("Start loading server ID now...");
            serverID = config.getServerName();
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
            HomeHandler homeHandler = new HomeHandler(cacheHandler, preIslandHandler);
            WarpHandler warpHandler = new WarpHandler(cacheHandler, preIslandHandler);
            LevelHandler levelHandler = new LevelHandler(config, cacheHandler);
            BanHandler banHandler = new BanHandler(cacheHandler, preIslandHandler);
            info("All main handlers loaded");

            info("Starting plugin messaging");
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            info("Plugin messaging loaded");

            info("Starting API");
            api = new NewSkyAPI(this, islandHandler, playerHandler, homeHandler, warpHandler, levelHandler, banHandler);
            info("API loaded");

            info("Starting all schedulers for the plugin");
            islandUnloadScheduler = new IslandUnloadScheduler(this, config, cacheHandler, worldHandler);
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
            islandUnloadScheduler.start();
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
        getServer().getPluginManager().registerEvents(new OnlinePlayersListener(this, cacheHandler, serverID), this);
        getServer().getPluginManager().registerEvents(new WorldInitListener(this), this);
        getServer().getPluginManager().registerEvents(new TeleportRequestListener(this, teleportHandler), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandMoveListener(config, cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandPvPListener(config, cacheHandler), this);
    }

    private void registerCommands() throws IOException, InvalidConfigurationException {
        commandManager = new PaperCommandManager(this);
        commandManager.enableUnstableAPI("help");
        commandManager.getLocales().setDefaultLocale(Locale.ENGLISH);
        commandManager.getLocales().loadYamlLanguageFile("messages.yml", Locale.ENGLISH);

        // Register the IslandCommand
        IslandCommand islandCommand = new IslandCommand(config, api);
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
                return getOnlinePlayers();
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
            info(ColorUtils.colorize(config.getDebugPrefix()) + message);
        }
    }

    // Get Online Players
    public Set<String> getOnlinePlayers() {
        return cacheHandler.getOnlinePlayers();
    }
}