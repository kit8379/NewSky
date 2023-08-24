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
import org.me.newsky.island.IslandHandler;
import org.me.newsky.redis.RedisHandler;

public class NewSky extends JavaPlugin {
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private IslandHandler islandHandler;
    private MVWorldManager mvWorldManager;

    @Override
    public void onEnable() {
        getLogger().info("Plugin enabling...");

        // Initialize Config
        getLogger().info("Start loading configuration now...");
        ConfigHandler config;
        try {
            config = new ConfigHandler(getConfig());
            getLogger().info("Config load success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Config load fail!");
        }

        // Initialize Multiverse-Core API
        Plugin mvCore = Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");
        if (!(mvCore != null && mvCore.isEnabled())) {
            throw new IllegalStateException("Multiverse-Core not found! Plugin will be disabled!");
        }

        // Initialize VoidGen
        Plugin voidGen = Bukkit.getServer().getPluginManager().getPlugin("voidgen");
        if (!(voidGen != null && voidGen.isEnabled())) {
            throw new IllegalStateException("Multiverse-Core not found! Plugin will be disabled!");
        }

        // Start Redis Connection
        getLogger().info("Start connecting to Redis now...");
        try {
            redisHandler = new RedisHandler(this, config, islandHandler);
            getLogger().info("Redis connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Redis Connection Fail! Plugin will be disabled!");
        }

        // Start Database connection
        getLogger().info("Start connecting to Database now...");
        try {
            databaseHandler = new DatabaseHandler(config.getDBHost(), config.getDBPort(), config.getDBName(), config.getDBUsername(), config.getDBPassword(), this);
            getLogger().info("Database connection success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Database connection fail! Plugin will be disabled!");

        }

        // Start Caching
        getLogger().info("Starting to cache into Redis");
        try {
            cacheHandler = new CacheHandler(redisHandler, databaseHandler);
            getLogger().info("Cache to Redis success");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Cache to Redis fail! Plugin will be disabled!");
        }

        // Start MVmanager
        getLogger().info("Starting MVmanager");
        try {
            mvWorldManager = ((MultiverseCore) mvCore).getMVWorldManager();
            getLogger().info("MVmanager loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("MVmanager load fail! Plugin will be disabled!");
        }

        // Start Island Handler
        getLogger().info("Starting island handler");
        try {
            islandHandler = new IslandHandler(config, mvWorldManager, redisHandler);
            getLogger().info("Islands loaded");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Islands load fail! Plugin will be disabled!");
        }

        getServer().getPluginManager().registerEvents(new WorldEventListener(this), this);

        // Registering the commands
        this.getCommand("islandadmin").setExecutor(new AdminCommandExecutor(cacheHandler, islandHandler));
        this.getCommand("island").setExecutor(new IslandCommandExecutor(cacheHandler, islandHandler));

        getLogger().info("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin disabling...");
        cacheHandler.saveCacheToDatabase();
        redisHandler.disconnect();
        databaseHandler.close();
        getLogger().info("Plugin disabled!");
    }
}
