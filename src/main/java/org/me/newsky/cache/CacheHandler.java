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

    public void cacheIslandDataToRedis() {
        databaseHandler.selectAllIslandData(resultSet -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                while (resultSet.next()) {
                    String islandUuid = resultSet.getString("island_uuid");
                    boolean lock = resultSet.getBoolean("lock");

                    Map<String, String> islandData = new HashMap<>();
                    islandData.put("lock", String.valueOf(lock));

                    jedis.hmset("island_data:" + islandUuid, islandData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void cacheIslandPlayersToRedis() {
        databaseHandler.selectAllIslandPlayers(resultSet -> {
            try (Jedis jedis = redisHandler.getJedis()) {
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
            try (Jedis jedis = redisHandler.getJedis()) {
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
            try (Jedis jedis = redisHandler.getJedis()) {
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
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> islandData = new HashMap<>();
            islandData.put("lock", "false");  // Adding default lock status

            jedis.hmset("island_data:" + islandUuid.toString(), islandData);
        }
        databaseHandler.addIslandData(islandUuid);
    }

    public void addIslandPlayer(UUID playerUuid, UUID islandUuid, String role) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> playerData = new HashMap<>();
            playerData.put("role", role);

            jedis.hmset("island_players:" + islandUuid + ":" + playerUuid, playerData);
        }
        databaseHandler.addIslandPlayer(playerUuid, islandUuid, role);
    }

    public void addOrUpdateWarpPoint(UUID playerUuid, String warpName, String warpLocation) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_warps:" + playerUuid.toString(), warpName, warpLocation);
        }
        databaseHandler.addOrUpdateWarpPoint(playerUuid, warpName, warpLocation);
    }

    public void addOrUpdateHomePoint(UUID playerUuid, String homeName, String homeLocation) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_homes:" + playerUuid.toString(), homeName, homeLocation);
        }
        databaseHandler.addOrUpdateHomePoint(playerUuid, homeName, homeLocation);
    }

    public void updateIslandLock(UUID islandUuid, boolean lock) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_data:" + islandUuid.toString(), "lock", String.valueOf(lock));
        }
        databaseHandler.updateIslandLock(islandUuid, lock);
    }

    public void deleteIsland(UUID islandUuid) {
        // Get all island members
        Set<UUID> members = getIslandMembers(islandUuid);

        // Delete homes and warps for each member
        members.forEach(memberUuid -> {
            deleteIslandPlayer(memberUuid, islandUuid);
        });

        // Finally, delete the island data
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island_data:" + islandUuid);
        }

        databaseHandler.deleteIsland(islandUuid);
    }

    public void deleteIslandPlayer(UUID playerUuid, UUID islandUuid) {
        // Delete player's homes and warps first
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island_homes:" + playerUuid.toString());
            jedis.del("island_warps:" + playerUuid.toString());
            jedis.del("island_players:" + islandUuid.toString() + ":" + playerUuid);
        }

        databaseHandler.deleteIslandPlayer(playerUuid, islandUuid);
    }

    public void deleteWarpPoint(UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_warps:" + playerUuid.toString(), warpName);
        }
        databaseHandler.deleteWarpPoint(playerUuid, warpName);
    }

    public void deleteHomePoint(UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_homes:" + playerUuid.toString(), homeName);
        }
        databaseHandler.deleteHomePoint(playerUuid, homeName);
    }

    public boolean getIslandLock(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String lock = jedis.hget("island_data:" + islandUuid.toString(), "lock");
            return Boolean.parseBoolean(lock);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Optional<String> getWarpLocation(UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedis()) {
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
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys("island_warps:" + playerUuid.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    public Optional<String> getHomeLocation(UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedis()) {
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
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys("island_homes:" + playerUuid.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    public Optional<UUID> getIslandOwner(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
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
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> keys = jedis.keys("island_players:" + islandUuid.toString() + ":*");
            for (String key : keys) {
                String[] parts = key.split(":");
                members.add(UUID.fromString(parts[2]));
            }
        }
        return members;
    }

    public Optional<UUID> getIslandUuidByPlayerUuid(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> keys = jedis.keys("island_players:*:" + playerUuid.toString());
            if (keys.isEmpty()) {
                return Optional.empty();
            }
            String key = keys.iterator().next();
            String[] segments = key.split(":");
            if (segments.length != 3) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(segments[1]));
        }
    }
}