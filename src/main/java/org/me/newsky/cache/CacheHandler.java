package org.me.newsky.cache;

import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.logging.Logger;

public class CacheHandler {

    private final Logger logger;
    private final RedisHandler redisHandler;
    private final DatabaseHandler databaseHandler;

    public CacheHandler(Logger logger, RedisHandler redisHandler, DatabaseHandler databaseHandler) {
        this.logger = logger;
        this.redisHandler = redisHandler;
        this.databaseHandler = databaseHandler;
    }

    public void cacheAllDataToRedis() {
        cacheIslandDataToRedis();
        cacheIslandPlayersToRedis();
    }

    private void cacheIslandDataToRedis() {
        databaseHandler.selectAllIslandData(resultSet -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                while (resultSet.next()) {
                    String islandUuid = resultSet.getString("island_uuid");
                    jedis.hset("island_data:" + islandUuid, "level", String.valueOf(resultSet.getInt("level")));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void cacheIslandPlayersToRedis() {
        databaseHandler.selectAllIslandPlayers(resultSet -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                while (resultSet.next()) {
                    String playerUuid = resultSet.getString("player_uuid");
                    String islandUuid = resultSet.getString("island_uuid");
                    String spawn = resultSet.getString("spawn");
                    String role = resultSet.getString("role");

                    Map<String, String> playerData = new HashMap<>();
                    playerData.put("spawn", spawn);
                    playerData.put("role", role);

                    jedis.hmset("island_players:" + islandUuid + ":" + playerUuid, playerData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void createIsland(UUID islandUuid) {
        // 1. Update the cache first.
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.hset("island_data:" + islandUuid.toString(), "level", "0");
        }

        // 2. Update the database asynchronously.
        databaseHandler.updateIslandData(islandUuid, 0);
    }

    public void deleteIsland(UUID islandUuid) {
        // 1. Remove from the cache.
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            // Deleting island data.
            jedis.del("island_data:" + islandUuid.toString());

            // Deleting all associated island players.
            Set<String> keys = jedis.keys("island_players:" + islandUuid + ":*");
            for (String key : keys) {
                jedis.del(key);
            }
        }

        // 2. Remove from the database asynchronously.
        databaseHandler.deleteIslandData(islandUuid);
    }

    public void addIslandPlayer(UUID playerUuid, UUID islandUuid, String spawnLocation, String role) {
        // 1. Update the cache first.
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            Map<String, String> playerData = new HashMap<>();
            playerData.put("spawn", spawnLocation);
            playerData.put("role", role);

            jedis.hmset("island_players:" + islandUuid + ":" + playerUuid, playerData);
        }

        // 2. Update the database asynchronously.
        databaseHandler.addIslandPlayer(playerUuid, islandUuid, spawnLocation, role);
    }

    public void deleteIslandPlayer(UUID playerUuid, UUID islandUuid) {
        // 1. Remove from the cache.
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.del("island_players:" + islandUuid.toString() + ":" + playerUuid.toString());
        }

        // 2. Remove from the database asynchronously.
        databaseHandler.deleteIslandPlayer(playerUuid, islandUuid);
    }

    public Optional<UUID> getIslandOwner(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            Set<String> keys = jedis.keys("island_players:" + islandUuid.toString() + ":*");
            for (String key : keys) {
                Map<String, String> data = jedis.hgetAll(key);
                if ("owner".equals(data.get("role"))) {
                    String[] parts = key.split(":");
                    return Optional.of(UUID.fromString(parts[2]));
                }
            }
        }
        return Optional.empty();
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        Set<UUID> members = new HashSet<>();
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            Set<String> keys = jedis.keys("island_players:" + islandUuid.toString() + ":*");
            for (String key : keys) {
                Map<String, String> data = jedis.hgetAll(key);
                if (!"owner".equals(data.get("role"))) {
                    String[] parts = key.split(":");
                    members.add(UUID.fromString(parts[2]));
                }
            }
        }
        return members;
    }

    public Optional<UUID> getIslandUuidByPlayerUuid(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            Set<String> keys = jedis.keys("island_players:*:" + playerUuid.toString());
            if (keys.size() == 0) {
                return Optional.empty();
            }

            // Extracting the island UUID from the key pattern "island_players:<islandUuid>:<playerUuid>"
            String key = keys.iterator().next();
            String[] segments = key.split(":");
            if (segments.length != 3) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(segments[1]));
        }
    }

    public Optional<String> getPlayerIslandSpawn(UUID playerUuid, UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            String key = "island_players:" + islandUuid.toString() + ":" + playerUuid.toString();

            // Check if the key exists in Redis
            if (!jedis.exists(key)) {
                return Optional.empty();
            }

            // Fetch the spawn location from the cache
            String spawn = jedis.hget(key, "spawn");

            if (spawn != null && !spawn.isEmpty()) {
                return Optional.of(spawn);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }
}
