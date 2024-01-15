package org.me.newsky.redis;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RedisOperation {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final MVWorldManager mvWorldManager;
    private final RedisHandler redisHandler;
    private final CacheHandler cacheHandler;

    public RedisOperation(NewSky plugin, ConfigHandler config, MVWorldManager mvWorldManager, RedisHandler redisHandler, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.config = config;
        this.mvWorldManager = mvWorldManager;
        this.redisHandler = redisHandler;
        this.cacheHandler = cacheHandler;
    }

    public CompletableFuture<Void> updateWorldList() {
        return CompletableFuture.runAsync(() -> {
            Set<String> worldNames = new HashSet<>();
            File serverDirectory = plugin.getServer().getWorldContainer();

            File[] files = serverDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        File sessionLock = new File(file, "session.lock");
                        File uidDat = new File(file, "uid.dat");
                        if (sessionLock.exists() && uidDat.exists()) {
                            worldNames.add(file.getName());
                        }
                    }
                }
            }

            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                String serverName = config.getServerName();
                String key = serverName + "_worlds";
                jedis.del(key);
                jedis.sadd(key, worldNames.toArray(new String[0]));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<String> getServerWithLeastWorlds(Map<String, String> outputMap) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Set<String>> serverWorlds = new HashMap<>();
            String serverId = null;
            int leastWorldCount = Integer.MAX_VALUE;

            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                Set<String> allKeys = jedis.keys("*_worlds");
                for (String key : allKeys) {
                    String serverName = key.split("_worlds")[0];
                    Set<String> worlds = jedis.smembers(key);
                    serverWorlds.put(serverName, worlds);
                }

                for (Map.Entry<String, Set<String>> entry : serverWorlds.entrySet()) {
                    int worldCount = entry.getValue().size();
                    if (worldCount < leastWorldCount) {
                        leastWorldCount = worldCount;
                        serverId = entry.getKey();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            outputMap.put("serverWithLeastWorlds", serverId);
            return serverId;
        });
    }

    public CompletableFuture<String> getServerByWorldName(String worldName, Map<String, String> outputMap) {
        return CompletableFuture.supplyAsync(() -> {
            String serverId = null;
            Map<String, Set<String>> serverWorlds = new HashMap<>();

            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                Set<String> allKeys = jedis.keys("*_worlds");
                for (String key : allKeys) {
                    String serverName = key.split("_worlds")[0];
                    Set<String> worlds = jedis.smembers(key);
                    serverWorlds.put(serverName, worlds);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (Map.Entry<String, Set<String>> entry : serverWorlds.entrySet()) {
                if (entry.getValue().contains(worldName)) {
                    serverId = entry.getKey();
                    break; // exit the loop once we found the world
                }
            }

            outputMap.put("serverByWorldName", serverId);  // Store the result in outputMap
            return serverId;
        });
    }

    public CompletableFuture<Void> createWorld(String worldName) {
        return CompletableFuture.runAsync(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            String generatorName = "VoidGen"; // Replace with your plugin's name
            World.Environment environment = World.Environment.NORMAL; // or NETHER, or THE_END
            WorldType worldType = WorldType.NORMAL; // or any other type you wish

            mvWorldManager.addWorld(worldName, environment, null, worldType, true, generatorName, false);
        }));
    }

    public CompletableFuture<Void> loadWorld(String worldName) {
        return CompletableFuture.runAsync(() -> Bukkit.getScheduler().runTask(plugin, () -> mvWorldManager.loadWorld(worldName)));
    }

    public CompletableFuture<Void> unloadWorld(String worldName) {
        return CompletableFuture.runAsync(() -> Bukkit.getScheduler().runTask(plugin, () -> mvWorldManager.unloadWorld(worldName)));
    }

    public CompletableFuture<Void> deleteWorld(String worldName) {
        return CompletableFuture.runAsync(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            mvWorldManager.unloadWorld(worldName);
            mvWorldManager.deleteWorld(worldName);
        }));
    }

    public CompletableFuture<Void> teleportToWorld(String playerName, String worldName) {
        return CompletableFuture.runAsync(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            World targetWorld = Bukkit.getWorld(worldName);
            Player player = Bukkit.getPlayer(playerName);
            if (targetWorld != null && player != null) {
                Optional<String> locationOptString = cacheHandler.getPlayerIslandSpawn(player.getUniqueId(), UUID.fromString(worldName));
                String locationString = locationOptString.orElse("0,100,0,0,0");
                String[] locParts = locationString.split(",");
                Location location = new Location(targetWorld, Double.parseDouble(locParts[0]), Double.parseDouble(locParts[1]), Double.parseDouble(locParts[2]), Float.parseFloat(locParts[3]), Float.parseFloat(locParts[4]));
                player.teleport(location);
            }
        }));
    }
}

