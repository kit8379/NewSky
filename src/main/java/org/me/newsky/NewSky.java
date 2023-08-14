package org.me.newsky;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.IslandAdminCommand;
import org.me.newsky.command.IslandCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.event.WorldEventListener;
import org.me.newsky.island.IslandHandler;
import org.me.newsky.redis.RedisHandler;

import java.util.Objects;

public class NewSky extends JavaPlugin {
    private ConfigHandler config;
    private RedisHandler redisHandler;
    private DatabaseHandler databaseHandler;
    private CacheHandler cacheHandler;
    private MVWorldManager mvWorldManager;
    private IslandHandler islandHandler;

    @Override
    public void onEnable() {
        getLogger().info("Plugin enabling...");

        // Initialize Config
        getLogger().info("Start loading configuration now...");
        try {
            config = new ConfigHandler(this);
            getLogger().info("Config load success!");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Config load fail!");
        }

        // Initialize Multiverse-Core API
        Plugin mvCore = Bukkit.getServer().getPluginManager().getPlugin("Multiverse-Core");
        if (mvCore instanceof MultiverseCore) {
            this.mvWorldManager = ((MultiverseCore) mvCore).getMVWorldManager();
        } else {
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
            redisHandler = new RedisHandler(config.getRedisHost(), config.getRedisPort(), config.getRedisPassword(),10, this);
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
            cacheHandler = new CacheHandler(this);
            getLogger().info("Cache to Redis success");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Cache to Redis fail! Plugin will be disabled!");
        }

        // Initialize the rest handlers
        islandHandler = new IslandHandler(this);

        getServer().getPluginManager().registerEvents(new WorldEventListener(this), this);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        Objects.requireNonNull(this.getCommand("island")).setExecutor(new IslandCommand(this));
        Objects.requireNonNull(this.getCommand("islandadmin")).setExecutor(new IslandAdminCommand(this));

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

    public ConfigHandler getConfigHandler() {
        return config;
    }

    public RedisHandler getRedisHandler() {
        return redisHandler;
    }

    public DatabaseHandler getDBHandler() {
        return databaseHandler;
    }

    public CacheHandler getCacheHandler() {
        return cacheHandler;
    }

    public MVWorldManager getMVWorldManager() {
        return mvWorldManager;
    }

    public IslandHandler getIslandHandler() {
        return islandHandler;
    }
}
