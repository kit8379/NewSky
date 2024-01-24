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
        cacheIslandWarpsToRedis();
        cacheIslandHomesToRedis();
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
                    String role = resultSet.getString("role");

                    Map<String, String> playerData = new HashMap<>();
                    playerData.put("role", role);

                    jedis.hmset("island_players:" + islandUuid + ":" + playerUuid, playerData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void cacheIslandWarpsToRedis() {
        databaseHandler.selectAllIslandWarps(resultSet -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                while (resultSet.next()) {
                    String playerUuid = resultSet.getString("player_uuid");
                    String warpName = resultSet.getString("warp_name");
                    String warpLocation = resultSet.getString("warp_location");

                    jedis.hset("island_warps:" + playerUuid, warpName, warpLocation);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void cacheIslandHomesToRedis() {
        databaseHandler.selectAllIslandHomes(resultSet -> {
            try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
                while (resultSet.next()) {
                    String playerUuid = resultSet.getString("player_uuid");
                    String homeName = resultSet.getString("home_name");
                    String homeLocation = resultSet.getString("home_location");

                    jedis.hset("island_homes:" + playerUuid, homeName, homeLocation);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void createIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.hset("island_data:" + islandUuid.toString(), "level", "0");
        }
        databaseHandler.updateIslandData(islandUuid, 0);
    }

    public void addIslandPlayer(UUID playerUuid, UUID islandUuid, String role) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            Map<String, String> playerData = new HashMap<>();
            playerData.put("role", role);

            jedis.hmset("island_players:" + islandUuid + ":" + playerUuid, playerData);
        }
        databaseHandler.addIslandPlayer(playerUuid, islandUuid, role);
    }

    public void addOrUpdateWarpPoint(UUID playerUuid, String warpName, String warpLocation) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.hset("island_warps:" + playerUuid.toString(), warpName, warpLocation);
        }
        databaseHandler.addWarpPoint(playerUuid, warpName, warpLocation);
    }

    public void addOrUpdateHomePoint(UUID playerUuid, String homeName, String homeLocation) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.hset("island_homes:" + playerUuid.toString(), homeName, homeLocation);
        }
        databaseHandler.addHomePoint(playerUuid, homeName, homeLocation);
    }

    public void deleteIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            Set<String> playerKeys = jedis.keys("island_players:" + islandUuid + ":*");
            for (String key : playerKeys) {
                jedis.del(key);
            }
        }
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.del("island_data:" + islandUuid.toString());
        }
        databaseHandler.deleteIslandData(islandUuid);
    }

    public void deleteIslandPlayer(UUID playerUuid, UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.del("island_players:" + islandUuid.toString() + ":" + playerUuid.toString());
        }
        databaseHandler.deleteIslandPlayer(playerUuid, islandUuid);
    }

    public void deleteWarpPoint(UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.hdel("island_warps:" + playerUuid.toString(), warpName);
        }
        databaseHandler.deleteWarpPoint(playerUuid, warpName);
    }

    public void deleteHomePoint(UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            jedis.hdel("island_homes:" + playerUuid.toString(), homeName);
        }
        databaseHandler.deleteHomePoint(playerUuid, homeName);
    }

    public Optional<String> getWarpLocation(UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            String key = "island_warps:" + playerUuid.toString();
            if (!jedis.exists(key)) {
                return Optional.empty();
            }
            return Optional.ofNullable(jedis.hget(key, warpName));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Set<String> getWarpNames(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            return jedis.hkeys("island_warps:" + playerUuid.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    public Optional<String> getHomeLocation(UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            String key = "island_homes:" + playerUuid.toString();
            if (!jedis.exists(key)) {
                return Optional.empty();
            }
            return Optional.ofNullable(jedis.hget(key, homeName));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Set<String> getHomeNames(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedisPool().getResource()) {
            return jedis.hkeys("island_homes:" + playerUuid.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
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
            if (keys.isEmpty()) {
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
}
