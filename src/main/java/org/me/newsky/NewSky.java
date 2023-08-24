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
import org.me.newsky.redis.RedisHandler;

public class NewSky extends JavaPlugin {
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private IslandHandler islandHandler;
    private MVWorldManager mvWorldManager;
    private ConfigHandler config;

    @Override
    public void onEnable() {
        getLogger().info("Plugin enabling...");

        initializeConfig();
        checkDependencies("Multiverse-Core", "voidgen");
        initializeRedis();
        initializeDatabase();
        initializeCache();
        initializeMVWorldManager();
        initializeIslandHandler();

        registerListeners();
        registerCommands();

        getLogger().info("Plugin enabled!");
    }

    private void initializeConfig() {
        getLogger().info("Start loading configuration now...");
        try {
            config = new ConfigHandler(getConfig());
            getLogger().info("Config load success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Config load fail!");
        }
    }

    private void checkDependencies(String... pluginNames) {
        for (String pluginName : pluginNames) {
            Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin(pluginName);
            if (!(plugin != null && plugin.isEnabled())) {
                throw new IllegalStateException(pluginName + " not found! Plugin will be disabled!");
            }
        }
    }

    private void initializeRedis() {
        getLogger().info("Start connecting to Redis now...");
        try {
            redisHandler = new RedisHandler(config);
            getLogger().info("Redis connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Connection Fail! Plugin will be disabled!");
        }
    }

    private void initializeDatabase() {
        getLogger().info("Start connecting to Database now...");
        try {
            databaseHandler = new DatabaseHandler(config.getDBHost(), config.getDBPort(), config.getDBName(), config.getDBUsername(), config.getDBPassword(), this);
            getLogger().info("Database connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Database connection fail! Plugin will be disabled!");
        }
    }

    private void initializeCache() {
        getLogger().info("Starting to cache into Redis");
        try {
            cacheHandler = new CacheHandler(redisHandler, databaseHandler);
            getLogger().info("Cache to Redis success");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Cache to Redis fail! Plugin will be disabled!");
        }
    }

    private void initializeMVWorldManager() {
        getLogger().info("Starting MVmanager");
        try {
            mvWorldManager = ((MultiverseCore) Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core")).getMVWorldManager();
            getLogger().info("MVmanager loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("MVmanager load fail! Plugin will be disabled!");
        }
    }

    private void initializeIslandHandler() {
        getLogger().info("Starting island handler");
        try {
            islandHandler = new IslandHandler(config, mvWorldManager, redisHandler);
            getLogger().info("Islands loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Islands load fail! Plugin will be disabled!");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new WorldEventListener(this), this);
    }

    private void registerCommands() {
        this.getCommand("islandadmin").setExecutor(new AdminCommandExecutor(cacheHandler, islandHandler));
        this.getCommand("island").setExecutor(new IslandCommandExecutor(cacheHandler, islandHandler));
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin disabling...");
        getLogger().info("Saving cache to database...");
        cacheHandler.saveCacheToDatabase();
        getLogger().info("Cache saved!");
        getLogger().info("Disconnecting from Redis...");
        redisHandler.disconnect();
        getLogger().info("Redis disconnected!");
        getLogger().info("Disconnecting from Database...");
        databaseHandler.close();
        getLogger().info("Database disconnected!");
        getLogger().info("Plugin disabled!");
    }
}
