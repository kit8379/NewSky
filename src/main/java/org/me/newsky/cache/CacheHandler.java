package org.me.newsky.cache;

import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.*;

public class CacheHandler {

    private final RedisHandler redisHandler;
    private final DatabaseHandler databaseHandler;

    public CacheHandler(RedisHandler redisHandler, DatabaseHandler databaseHandler) {
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

    public void deleteIsland(UUID islandUuid) {
        // 1. Remove associated island players from the cache.
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            Set<String> playerKeys = jedis.keys("island_players:" + islandUuid + ":*");
            for (String key : playerKeys) {
                jedis.del(key);
            }
        }

        // 2. Remove island data from the cache.
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.del("island_data:" + islandUuid.toString());
        }

        // 3. Remove island data from the database asynchronously.
        databaseHandler.deleteIslandData(islandUuid);
    }


    public void deleteIslandPlayer(UUID playerUuid, UUID islandUuid) {
        // 1. Remove from the cache.
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.del("island_players:" + islandUuid.toString() + ":" + playerUuid.toString());
        }

        // 2. Remove from the database asynchronously.
        databaseHandler.deleteIslandPlayer(playerUuid, islandUuid);
    }

    /**
     * This method returns the island level of an island.
     * @param islandUuid UUID of the island.
     * @return Island level of the island. 0 if the island does not exist.
     */
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

    /**
     * This method returns all members of an island.
     *
     * @param islandUuid UUID of the island.
     * @return Set of UUIDs of all members of the island. Empty if the island has no members.
     */
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

    /**
     * This method returns the island UUID of a player.
     *
     * @param playerUuid UUID of the player.
     * @return Optional island UUID of the player. Empty if the player is not on an island.
     */
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

    /**
     * This method returns the spawn location of a player on a specific island.
     *
     * @param playerUuid - The UUID of the player.
     * @param islandUuid - The UUID of the island.
     * @return Optional containing the spawn location, or an empty Optional if the spawn location isn't found.
     */
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
