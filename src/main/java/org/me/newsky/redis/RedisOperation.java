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

    public void updateWorldList(Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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

                // Run the callback after jedis operations complete
                callback.run();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void getServerWithLeastWorlds(Runnable callback, Map<String, String> outputMap) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Set<String>> serverWorlds = new HashMap<>();

            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                Set<String> allKeys = jedis.keys("*_worlds");

                for (String key : allKeys) {
                    String serverName = key.split("_worlds")[0];
                    Set<String> worlds = jedis.smembers(key);
                    serverWorlds.put(serverName, worlds);
                }

                String serverId = null;
                int leastWorldCount = Integer.MAX_VALUE;
                for (Map.Entry<String, Set<String>> entry : serverWorlds.entrySet()) {
                    int worldCount = entry.getValue().size();
                    if (worldCount < leastWorldCount) {
                        leastWorldCount = worldCount;
                        serverId = entry.getKey();
                    }
                }

                outputMap.put("serverWithLeastWorlds", serverId);  // Store the result in outputMap
                callback.run();  // Execute the callback

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void getServerByWorldName(String worldName, Runnable callback, Map<String, String> outputMap) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
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
                e.printStackTrace();
            }

            for (Map.Entry<String, Set<String>> entry : serverWorlds.entrySet()) {
                if (entry.getValue().contains(worldName)) {
                    serverId = entry.getKey();
                    break; // exit the loop once we found the world
                }
            }

            outputMap.put("serverByWorldName", serverId);  // Store the result in outputMap
            callback.run();  // Execute the callback
        });
    }

    public void createWorld(String worldName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                String generatorName = "VoidGen"; // Replace with your plugin's name
                World.Environment environment = World.Environment.NORMAL; // or NETHER, or THE_END
                WorldType worldType = WorldType.NORMAL; // or any other type you wish

                mvWorldManager.addWorld(worldName, environment, null, worldType, true, generatorName, false);
            });
            // Run the callback after jedis operations complete
            callback.run();
        });
    }

    public void loadWorld(String worldName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> mvWorldManager.loadWorld(worldName));
            // Run the callback after jedis operations complete
            callback.run();
        });
    }

    public void unloadWorld(String worldName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> mvWorldManager.unloadWorld(worldName));
            // Run the callback after jedis operations complete
            callback.run();
        });
    }

    public void deleteWorld(String worldName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                mvWorldManager.unloadWorld(worldName);
                mvWorldManager.deleteWorld(worldName);
            });
            // Run the callback after jedis operations complete
            callback.run();
        });
    }

    public void teleportToWorld(String worldName, String playerName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                World targetWorld = Bukkit.getWorld(worldName);
                Player player = Bukkit.getPlayer(playerName);
                if (targetWorld != null && player != null) {
                    Optional<String> locationOptString = cacheHandler.getPlayerIslandSpawn(player.getUniqueId(), UUID.fromString(worldName));
                    String locationString;
                    if(locationOptString.isEmpty()) {
                        locationString = "0,100,0,0,0";
                    } else {
                        locationString = locationOptString.get();
                    }
                    String locationX = locationString.split(",")[0];
                    String locationY = locationString.split(",")[1];
                    String locationZ = locationString.split(",")[2];
                    String locationYaw = locationString.split(",")[3];
                    String locationPitch = locationString.split(",")[4];
                    Location location = new Location(targetWorld, Double.parseDouble(locationX), Double.parseDouble(locationY), Double.parseDouble(locationZ), Float.parseFloat(locationYaw), Float.parseFloat(locationPitch));
                    player.teleport(location);
                }
            });
            // Run the callback after jedis operations complete
            callback.run();
        });
    }
}
