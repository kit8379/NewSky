package org.me.newsky.redis;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RedisOperation {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final MVWorldManager mvWorldManager;
    private final RedisHandler redisHandler;

    public RedisOperation(NewSky plugin, ConfigHandler config, MVWorldManager mvWorldManager, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.config = config;
        this.mvWorldManager = mvWorldManager;
        this.redisHandler = redisHandler;
    }

    public void updateWorldList(Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Set<String> worldNames = new HashSet<>();
            File serverDirectory = plugin.getServer().getWorldContainer();

            File[] files = serverDirectory.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        File levelDat = new File(file, "level.dat");
                        if (levelDat.exists()) {
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

    public void getAllWorlds(Runnable callback, Map<String, Set<String>> outputMap) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Set<String>> serverWorlds = new HashMap<>();

            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                Set<String> allKeys = jedis.keys("*_worlds");

                for (String key : allKeys) {
                    String serverName = key.split("_worlds")[0];
                    Set<String> worlds = jedis.smembers(key);
                    serverWorlds.put(serverName, worlds);
                }

                outputMap.putAll(serverWorlds);
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

                if (mvWorldManager.addWorld(worldName, environment, null, worldType, true, generatorName, false)) {
                    Bukkit.getLogger().info("World created successfully!");
                } else {
                    Bukkit.getLogger().severe("Failed to create world!");
                }
            });
            // Run the callback after jedis operations complete
            callback.run();
        });
    }

    public void loadWorld(String worldName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (mvWorldManager.loadWorld(worldName)) {
                    Bukkit.getLogger().info("World loaded successfully!");
                } else {
                    Bukkit.getLogger().severe("Failed to load world!");
                }
            });
            // Run the callback after jedis operations complete
            callback.run();
        });
    }

    public void unloadWorld(String worldName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (mvWorldManager.unloadWorld(worldName)) {
                    Bukkit.getLogger().info("World unloaded successfully!");
                } else {
                    Bukkit.getLogger().severe("Failed to unload world!");
                }
            });
            // Run the callback after jedis operations complete
            callback.run();
        });
    }

    public void deleteWorld(String worldName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean successUnload = mvWorldManager.unloadWorld(worldName);
                boolean successDelete = mvWorldManager.deleteWorld(worldName);
                if (successUnload && successDelete) {
                    Bukkit.getLogger().info("World deleted successfully!");
                } else {
                    Bukkit.getLogger().severe("Failed to delete world!");
                }
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
                    player.teleport(targetWorld.getSpawnLocation());
                }
            });
            // Run the callback after jedis operations complete
            callback.run();
        });
    }
}
