package org.me.newsky.island;

import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldType;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;
import org.me.newsky.teleport.TeleportManager;
import redis.clients.jedis.Jedis;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class IslandOperation {

    private final NewSky plugin;
    private final Logger logger;
    private final ConfigHandler config;
    private final MVWorldManager mvWorldManager;
    private final CacheHandler cacheHandler;
    private final RedisHandler redisHandler;
    private final TeleportManager teleportManager;

    public IslandOperation(NewSky plugin, Logger logger, ConfigHandler config, MVWorldManager mvWorldManager, CacheHandler cacheHandler, RedisHandler redisHandler, TeleportManager teleportManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
        this.mvWorldManager = mvWorldManager;
        this.cacheHandler = cacheHandler;
        this.redisHandler = redisHandler;
        this.teleportManager = teleportManager;
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
            logger.info("Creating world " + worldName);
            String generatorName = "VoidGen";
            World.Environment environment = World.Environment.NORMAL;
            WorldType worldType = WorldType.NORMAL;
            mvWorldManager.addWorld(worldName, environment, null, worldType, true, generatorName, false);
            logger.info("Created world " + worldName);
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

    public CompletableFuture<Void> teleportToWorld(String worldUuidString, String playerUuidString) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        // Asynchronous task to fetch island spawn
        CompletableFuture.runAsync(() -> {
            UUID playerUuid = UUID.fromString(playerUuidString);
            UUID worldUuid = UUID.fromString(worldUuidString);

            Optional<String> islandSpawn = cacheHandler.getPlayerIslandSpawn(playerUuid, worldUuid);

            if (islandSpawn.isEmpty()) {
                islandSpawn = Optional.of("0,100,0,0,0");
            }

            String[] parts = islandSpawn.get().split(",");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = Float.parseFloat(parts[3]);
            float pitch = Float.parseFloat(parts[4]);

            // Switching back to the main thread to interact with the Minecraft world
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location location = new Location(Bukkit.getWorld(worldUuid), x, y, z, yaw, pitch);
                teleportManager.addPendingTeleport(playerUuid, location);
                future.complete(null);

            });
        }).exceptionally(e -> {
            future.completeExceptionally(e);
            return null;
        });

        return future;
    }
}

