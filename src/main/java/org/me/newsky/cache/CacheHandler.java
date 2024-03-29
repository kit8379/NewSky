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
        cacheIslandHomesToRedis();
        cacheIslandWarpsToRedis();
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

    private void cacheIslandHomesToRedis() {
        databaseHandler.selectAllIslandHomes(resultSet -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                while (resultSet.next()) {
                    String playerUuid = resultSet.getString("player_uuid");
                    String islandUuid = resultSet.getString("island_uuid");
                    String homeName = resultSet.getString("home_name");
                    String homeLocation = resultSet.getString("home_location");

                    // Adjusting key to include island_uuid for better association
                    jedis.hset("island_homes:" + islandUuid + ":" + playerUuid, homeName, homeLocation);
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
                    String islandUuid = resultSet.getString("island_uuid");
                    String warpName = resultSet.getString("warp_name");
                    String warpLocation = resultSet.getString("warp_location");

                    // Adjusting key to include island_uuid for better association
                    jedis.hset("island_warps:" + islandUuid + ":" + playerUuid, warpName, warpLocation);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void createIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> islandData = new HashMap<>();
            islandData.put("lock", "false");
            islandData.put("pvp", "false");

            jedis.hmset("island_data:" + islandUuid.toString(), islandData);
        }
        databaseHandler.addIslandData(islandUuid);
    }

    public void addOrUpdateIslandPlayer(UUID playerUuid, UUID islandUuid, String role) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> playerData = new HashMap<>();
            playerData.put("role", role);

            jedis.hmset("island_players:" + islandUuid + ":" + playerUuid, playerData);
        }
        databaseHandler.addOrUpdateIslandPlayer(playerUuid, islandUuid, role);
    }

    public void addOrUpdateWarpPoint(UUID playerUuid, UUID islandUuid, String warpName, String warpLocation) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_warps:" + islandUuid + ":" + playerUuid, warpName, warpLocation);
        }
        // Update database call to include islandUuid
        databaseHandler.addOrUpdateWarpPoint(playerUuid, islandUuid, warpName, warpLocation);
    }

    public void addOrUpdateHomePoint(UUID playerUuid, UUID islandUuid, String homeName, String homeLocation) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_homes:" + islandUuid + ":" + playerUuid, homeName, homeLocation);
        }
        // Update database call to include islandUuid
        databaseHandler.addOrUpdateHomePoint(playerUuid, islandUuid, homeName, homeLocation);
    }

    public void updateIslandLock(UUID islandUuid, boolean lock) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_data:" + islandUuid.toString(), "lock", String.valueOf(lock));
        }
        databaseHandler.updateIslandLock(islandUuid, lock);
    }

    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_data:" + islandUuid.toString(), "pvp", String.valueOf(pvp));
        }
        databaseHandler.updateIslandPvp(islandUuid, pvp);
    }

    public void updateIslandOwner(UUID playerUuid, UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> keys = jedis.keys("island_players:" + islandUuid + ":*");
            for (String key : keys) {
                Map<String, String> data = jedis.hgetAll(key);
                if ("owner".equals(data.get("role"))) {
                    jedis.hset(key, "role", "member");
                }
            }
            jedis.hset("island_players:" + islandUuid + ":" + playerUuid, "role", "owner");
        }

        databaseHandler.updateIslandOwner(playerUuid, islandUuid);
    }

    public void deleteIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.keys("island_warps:" + islandUuid + ":*").forEach(jedis::del);
            jedis.keys("island_homes:" + islandUuid + ":*").forEach(jedis::del);
            jedis.keys("island_players:" + islandUuid + ":*").forEach(jedis::del);
            jedis.del("island_data:" + islandUuid);
        }

        databaseHandler.deleteIsland(islandUuid);
    }

    public void deleteIslandPlayer(UUID playerUuid, UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island_players:" + islandUuid + ":" + playerUuid);
        }

        databaseHandler.deleteIslandPlayer(playerUuid, islandUuid);
    }

    public void deleteWarpPoint(UUID playerUuid, UUID islandUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_warps:" + islandUuid + ":" + playerUuid, warpName);
        }
        databaseHandler.deleteWarpPoint(playerUuid, islandUuid, warpName);
    }

    public void deleteHomePoint(UUID playerUuid, UUID islandUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_homes:" + islandUuid + ":" + playerUuid, homeName);
        }
        databaseHandler.deleteHomePoint(playerUuid, islandUuid, homeName);
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

    public boolean getIslandPvp(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String pvp = jedis.hget("island_data:" + islandUuid.toString(), "pvp");
            return Boolean.parseBoolean(pvp);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public Optional<String> getWarpLocation(UUID islandUuid, UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String key = "island_warps:" + islandUuid + ":" + playerUuid;
            if (!jedis.exists(key)) {
                return Optional.empty();
            }
            return Optional.ofNullable(jedis.hget(key, warpName));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Set<String> getWarpNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String key = "island_warps:" + islandUuid + ":" + playerUuid;
            return jedis.hkeys(key);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptySet();
        }
    }

    public Optional<String> getHomeLocation(UUID islandUuid, UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String key = "island_homes:" + islandUuid + ":" + playerUuid;
            if (!jedis.exists(key)) {
                return Optional.empty();
            }
            return Optional.ofNullable(jedis.hget(key, homeName));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public Set<String> getHomeNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String key = "island_homes:" + islandUuid + ":" + playerUuid;
            return jedis.hkeys(key);
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