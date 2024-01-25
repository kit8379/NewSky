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
import org.me.newsky.event.*;
import org.me.newsky.heartbeat.HeartBeatHandler;
import org.me.newsky.island.IslandHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.scheduler.WorldUnloadHandler;
import org.me.newsky.teleport.TeleportManager;
import org.me.newsky.world.WorldHandler;

import java.util.Objects;

public class NewSky extends JavaPlugin {

    private String serverID;
    private ConfigHandler config;
    private MVWorldManager mvWorldManager;
    private WorldHandler worldHandler;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private TeleportManager teleportManager;
    private HeartBeatHandler heartBeatHandler;
    private WorldUnloadHandler worldUnloadHandler;
    private IslandHandler islandHandler;

    @Override
    public void onEnable() {
        info("Plugin enabling...");
        initalize();
        info("Plugin enabled!");
    }

    private void initalize() {
        checkDependencies("Multiverse-Core");
        initializeConfig();
        initalizeServerID();
        initializeMVWorldManager();
        initializeWorldHandler();
        initializeRedis();
        initializeDatabase();
        initializeCache();
        initializeTeleportManager();
        initalizeheartBeatHandler();
        initalizeWorldUnloadHandler();
        initalizePluginMessaging();
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

    private void initializeMVWorldManager() {
        info("Starting MVmanager");
        try {
            mvWorldManager = ((MultiverseCore) Objects.requireNonNull(Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core"))).getMVWorldManager();
            info("MVmanager loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("MVmanager load fail! Plugin will be disabled!");
        }
    }

    private void initializeWorldHandler() {
        info("Starting WorldHandler");
        try {
            worldHandler = new WorldHandler(this, mvWorldManager);
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
            heartBeatHandler = new HeartBeatHandler(this, redisHandler, serverID, config.getServerMode());
            heartBeatHandler.startHeartBeat();
            info("Heart Beat started!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Heart Beat Fail! Plugin will be disabled!");
        }
    }

    private void initalizeWorldUnloadHandler() {
        info("Starting world unload handler");
        try {
            worldUnloadHandler = new WorldUnloadHandler(this, worldHandler);
            worldUnloadHandler.startWorldUnloadTask();
            info("World unload handler loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("World unload handler load fail! Plugin will be disabled!");
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
            islandHandler = new IslandHandler(this, worldHandler, redisHandler, heartBeatHandler, teleportManager, serverID);
            islandHandler.subscribeToRequests();
            info("Islands loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Islands load fail! Plugin will be disabled!");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WorldEventListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinEventListener(this, teleportManager), this);
        getServer().getPluginManager().registerEvents(new TeleportEventListener(cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(cacheHandler), this);
        getServer().getPluginManager().registerEvents(new IslandBoundaryListener(), this);
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
        worldUnloadHandler.stopWorldUnloadTask();
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
            getLogger().info("§bDEBUG: " + message);
        }
    }
}
