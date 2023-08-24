package org.me.newsky.island;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.redis.RedisPubSubResponse;

public class IslandHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final MVWorldManager mvWorldManager;
    private final RedisHandler redisHandler;
    private final RedisPubSubResponse redisPubSubResponse;

    public IslandHandler(NewSky plugin, ConfigHandler config, MVWorldManager mvWorldManager, RedisHandler redisHandler, RedisPubSubResponse redisPubSubResponse) {
        this.plugin = plugin;
        this.config = config;
        this.mvWorldManager = mvWorldManager;
        this.redisHandler = redisHandler;
        this.redisPubSubResponse = redisPubSubResponse;
    }

    public void createWorldOperation(String worldName) {
        String generatorName = "VoidGen"; // Replace with your plugin's name
        World.Environment environment = World.Environment.NORMAL; // or NETHER, or THE_END
        WorldType worldType = WorldType.NORMAL; // or any other type you wish

        if (mvWorldManager.addWorld(worldName, environment, null, worldType, true, generatorName, false)) {
            Bukkit.getLogger().info("World created successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to create world!");
        }
    }

    public void loadWorldOperation(String worldName) {
        if (mvWorldManager.loadWorld(worldName)) {
            Bukkit.getLogger().info("World loaded successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to load world!");
        }
    }

    public void unloadWorldOperation(String worldName) {
        if (mvWorldManager.unloadWorld(worldName)) {
            Bukkit.getLogger().info("World unloaded successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to unload world!");
        }
    }

    public void deleteWorldOperation(String worldName) {
        boolean successUnload = mvWorldManager.unloadWorld(worldName);
        boolean successDelete = mvWorldManager.deleteWorld(worldName);
        if (successUnload && successDelete) {
            Bukkit.getLogger().info("World deleted successfully!");
        } else {
            Bukkit.getLogger().severe("Failed to delete world!");
        }
    }


    public void teleportToSpawnOperation(String worldName, String playerName) {
        World targetWorld = Bukkit.getWorld(worldName);
        Player player = Bukkit.getPlayer(playerName);
        if (targetWorld != null && player != null) {
            player.teleport(targetWorld.getSpawnLocation());
        }
    }

    public void createWorld(String worldName) {

        String targetServer = islandRedisOperation.findServerWithLeastWorlds();

        if (targetServer == null || targetServer.equals(config.getServerName())) {
            createWorldOperation(worldName);
            return;
        }

        islandRedisOperation.addWorldToServer(targetServer, worldName);
    }

    public void loadWorld(String worldName) {
        String targetServer = redisHandler.findServerWithWorld(worldName);

        if (targetServer == null || targetServer.equals(config.getServerName())) {
            loadWorldOperation(worldName);
            return;
        }

        islandRedisOperation.loadWorldFromServer(targetServer, worldName);
    }

    public void unloadWorld(String worldName) {
        String targetServer = redisHandler.findServerWithWorld(worldName);

        if (targetServer == null || targetServer.equals(config.getServerName())) {
            unloadWorldOperation(worldName);
            return;
        }

        islandRedisOperation.unloadWorldFromServer(targetServer, worldName);
    }

    public void deleteWorld(String worldName) {
        String targetServer = redisHandler.findServerWithWorld(worldName);

        if (targetServer == null || targetServer.equals(config.getServerName())) {
            deleteWorldOperation(worldName);
            return;
        }

        islandRedisOperation.deleteWorldFromServer(targetServer, worldName);
    }

    public void teleportToSpawn(Player player, String worldName) {
        String targetServer = redisHandler.findServerWithWorld(worldName);

        if (targetServer == null || targetServer.equals(config.getServerName())) {
            teleportToSpawnOperation(worldName, player.getName());
            return;
        }

        islandRedisOperation.teleportToSpawnFromServer(targetServer, worldName, player.getName());
    }
}