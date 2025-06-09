package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.logging.Level;

public class CacheHandler {

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final DatabaseHandler databaseHandler;

    public CacheHandler(NewSky plugin, RedisHandler redisHandler, DatabaseHandler databaseHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.databaseHandler = databaseHandler;
    }

    public void cacheAllDataToRedis() {
        flushAllDataFromRedis();
        cacheIslandDataToRedis();
        cacheIslandPlayersToRedis();
        cacheIslandHomesToRedis();
        cacheIslandWarpsToRedis();
        cacheIslandLevelsToRedis();
        cacheIslandBansToRedis();
    }

    // Check is there online server
    // If not, then this server is the first server startup in the network
    // Then we can flush all data related to this plugin before cache in
    // This can prevent data inconsistency if the user directly modify the database data
    public void flushAllDataFromRedis() {
        if (getActiveServers().isEmpty()) {
            try (Jedis jedis = redisHandler.getJedis()) {
                jedis.keys("island_bans:*").forEach(jedis::del);
                jedis.del("island_levels");
                jedis.keys("island_warps:*").forEach(jedis::del);
                jedis.keys("island_homes:*").forEach(jedis::del);
                jedis.keys("island_players:*").forEach(jedis::del);
                jedis.keys("island_data:*").forEach(jedis::del);
            }
        }
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
                plugin.getLogger().log(Level.SEVERE, "Error while caching island data to Redis", e);
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
                plugin.getLogger().log(Level.SEVERE, "Error while caching island players to Redis", e);
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
                    jedis.hset("island_homes:" + islandUuid + ":" + playerUuid, homeName, homeLocation);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error while caching island homes to Redis", e);
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
                    jedis.hset("island_warps:" + islandUuid + ":" + playerUuid, warpName, warpLocation);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error while caching island warps to Redis", e);
            }
        });
    }

    private void cacheIslandLevelsToRedis() {
        databaseHandler.selectAllIslandLevels(resultSet -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                while (resultSet.next()) {
                    String islandUuid = resultSet.getString("island_uuid");
                    int level = resultSet.getInt("level");
                    jedis.hset("island_levels", islandUuid, String.valueOf(level));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error while caching island levels to Redis", e);
            }
        });
    }

    public void cacheIslandBansToRedis() {
        databaseHandler.selectAllIslandBans(resultSet -> {
            try (Jedis jedis = redisHandler.getJedis()) {
                while (resultSet.next()) {
                    String islandUuid = resultSet.getString("island_uuid");
                    String bannedPlayer = resultSet.getString("banned_player");
                    jedis.sadd("island_bans:" + islandUuid, bannedPlayer);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error while caching island bans to Redis", e);
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

    public void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> playerData = new HashMap<>();
            playerData.put("role", role);

            jedis.hmset("island_players:" + islandUuid + ":" + playerUuid, playerData);
        }
        databaseHandler.updateIslandPlayer(islandUuid, playerUuid, role);
    }

    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_homes:" + islandUuid + ":" + playerUuid, homeName, homeLocation);
        }
        // Update database call to include islandUuid
        databaseHandler.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);
    }

    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_warps:" + islandUuid + ":" + playerUuid, warpName, warpLocation);
        }
        // Update database call to include islandUuid
        databaseHandler.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);
    }

    public void updateIslandOwner(UUID islandUuid, UUID playerUuid) {
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

        databaseHandler.updateIslandOwner(islandUuid, playerUuid);
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

    public void updateIslandLevel(UUID islandUuid, int level) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_levels", islandUuid.toString(), String.valueOf(level));
        }
        databaseHandler.updateIslandLevel(islandUuid, level);
    }

    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.sadd("island_bans:" + islandUuid.toString(), playerUuid.toString());
        }
        databaseHandler.updateBanPlayer(islandUuid, playerUuid);
    }

    public void deleteIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island_bans:" + islandUuid.toString());
            jedis.hdel("island_levels", islandUuid.toString());
            jedis.keys("island_warps:" + islandUuid + ":*").forEach(jedis::del);
            jedis.keys("island_homes:" + islandUuid + ":*").forEach(jedis::del);
            jedis.keys("island_players:" + islandUuid + ":*").forEach(jedis::del);
            jedis.del("island_data:" + islandUuid);
            databaseHandler.deleteIsland(islandUuid);
        }
    }

    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island_players:" + islandUuid + ":" + playerUuid);
        }

        databaseHandler.deleteIslandPlayer(islandUuid, playerUuid);
    }

    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_homes:" + islandUuid + ":" + playerUuid, homeName);
        }
        databaseHandler.deleteHomePoint(islandUuid, playerUuid, homeName);
    }

    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_warps:" + islandUuid + ":" + playerUuid, warpName);
        }
        databaseHandler.deleteWarpPoint(islandUuid, playerUuid, warpName);
    }

    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.srem("island_bans:" + islandUuid.toString(), playerUuid.toString());
        }
        databaseHandler.deleteBanPlayer(islandUuid, playerUuid);
    }

    public boolean isIslandLock(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String lock = jedis.hget("island_data:" + islandUuid.toString(), "lock");
            return Boolean.parseBoolean(lock);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island lock status", e);
            return false;
        }
    }

    public boolean isIslandPvp(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String pvp = jedis.hget("island_data:" + islandUuid.toString(), "pvp");
            return Boolean.parseBoolean(pvp);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island pvp status", e);
            return false;
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
            plugin.getLogger().log(Level.SEVERE, "Error while getting home location", e);
            return Optional.empty();
        }
    }

    public Set<String> getHomeNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String key = "island_homes:" + islandUuid + ":" + playerUuid;
            return jedis.hkeys(key);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting home names", e);
            return Collections.emptySet();
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
            plugin.getLogger().log(Level.SEVERE, "Error while getting warp location", e);
            return Optional.empty();
        }
    }

    public Set<String> getWarpNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String key = "island_warps:" + islandUuid + ":" + playerUuid;
            return jedis.hkeys(key);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting warp names", e);
            return Collections.emptySet();
        }
    }

    public int getIslandLevel(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String level = jedis.hget("island_levels", islandUuid.toString());
            return level != null ? Integer.parseInt(level) : 0;
        }
    }

    public Map<UUID, Integer> getTopIslandLevels(int size) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> islandLevels = jedis.hgetAll("island_levels");
            return islandLevels.entrySet().stream().map(entry -> new AbstractMap.SimpleEntry<>(UUID.fromString(entry.getKey()), Integer.parseInt(entry.getValue()))).sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(size).collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
        }
    }


    public boolean isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.sismember("island_bans:" + islandUuid.toString(), playerUuid.toString());
        }
    }

    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> bannedPlayers = jedis.smembers("island_bans:" + islandUuid.toString());
            Set<UUID> bannedPlayerUuids = new HashSet<>();
            for (String bannedPlayer : bannedPlayers) {
                bannedPlayerUuids.add(UUID.fromString(bannedPlayer));
            }
            return bannedPlayerUuids;
        }
    }

    public UUID getIslandOwner(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> keys = jedis.keys("island_players:" + islandUuid.toString() + ":*");
            for (String key : keys) {
                Map<String, String> data = jedis.hgetAll(key);
                if ("owner".equals(data.get("role"))) {
                    String[] parts = key.split(":");
                    return UUID.fromString(parts[2]);
                }
            }
        }
        return null;
    }

    public Set<UUID> getIslandMembers(UUID islandUuid) {
        Set<UUID> members = new HashSet<>();
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> keys = jedis.keys("island_players:" + islandUuid.toString() + ":*");
            for (String key : keys) {
                Map<String, String> data = jedis.hgetAll(key);
                if ("member".equals(data.get("role"))) {
                    String[] parts = key.split(":");
                    members.add(UUID.fromString(parts[2]));
                }
            }
        }
        return members;
    }

    public Set<UUID> getIslandPlayers(UUID islandUuid) {
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

    public Optional<UUID> getIslandUuid(UUID playerUuid) {
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

    public void addCoopPlayer(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.sadd("coop_players:" + islandUuid, playerUuid.toString());
        }
    }

    public void removeCoopPlayer(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.srem("coop_players:" + islandUuid, playerUuid.toString());
        }
    }

    public void removeAllCoopOfPlayer(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> keys = jedis.keys("coop_players:*");
            for (String key : keys) {
                jedis.srem(key, playerUuid.toString());
            }
        }
    }

    public boolean isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.sismember("coop_players:" + islandUuid, playerUuid.toString());
        }
    }

    public Set<UUID> getCoopedPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> raw = jedis.smembers("coop_players:" + islandUuid);
            Set<UUID> result = new HashSet<>();
            for (String uuidStr : raw) {
                result.add(UUID.fromString(uuidStr));
            }
            return result;
        }
    }

    /**
     * Update the active server's heartbeat in Redis
     *
     * @param serverName The name of the server to update
     */
    public void updateActiveServer(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("server_heartbeats", serverName, String.valueOf(System.currentTimeMillis()));
        }
    }

    /**
     * Remove the active server from Redis
     *
     * @param serverName The name of the server to remove
     */
    public void removeActiveServer(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("server_heartbeats", serverName);
            // Remove island server association
            Map<String, String> islandServerMap = jedis.hgetAll("island_server");
            for (Map.Entry<String, String> entry : islandServerMap.entrySet()) {
                if (entry.getValue().equals(serverName)) {
                    jedis.hdel("island_server", entry.getKey());
                }
            }
            // Remove online players from server
            Map<String, String> onlinePlayers = jedis.hgetAll("online_players");
            for (Map.Entry<String, String> entry : onlinePlayers.entrySet()) {
                if (entry.getValue().equals(serverName)) {
                    jedis.hdel("online_players", entry.getKey());
                }
            }
        }
    }

    /**
     * Get all active servers from Redis
     *
     * @return A map of server names and their last heartbeat timestamps
     */
    public Map<String, String> getActiveServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hgetAll("server_heartbeats");
        }
    }

    /**
     * Update the server name for a specific island UUID in Redis.
     *
     * @param islandUuid The UUID of the island.
     * @param serverName The name of the server where the island is loaded.
     */
    public void updateIslandLoadedServer(UUID islandUuid, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_server", islandUuid.toString(), serverName);
        }
    }

    /**
     * Remove the server name for a specific island UUID in Redis.
     *
     * @param islandUuid The UUID of the island.
     */
    public void removeIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_server", islandUuid.toString());
        }
    }

    /**
     * Get the server name for a specific island UUID from Redis.
     *
     * @param islandUuid The UUID of the island.
     * @return The name of the server where the island is loaded, or null if not found.
     */
    public Optional<String> getIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String server = jedis.hget("island_server", islandUuid.toString());
            return Optional.ofNullable(server);
        }
    }

    /**
     * Add an online player to the Redis cache.
     *
     * @param playerName The name of the player.
     * @param serverName The name of the server where the player is online.
     */
    public void addOnlinePlayer(String playerName, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("online_players", playerName, serverName);
        }
    }

    /**
     * Remove an online player from the Redis cache.
     *
     * @param playerName The name of the player.
     * @param serverName The name of the server where the playerwas online.
     */
    public void removeOnlinePlayer(String playerName, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("online_players", playerName, serverName);
        }
    }

    /**
     * Get the set of online players from Redis.
     *
     * @return A set of player names currently online.
     */
    public Set<String> getOnlinePlayers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys("online_players");
        }
    }

    /**
     * Get the server name where the player is currently online.
     *
     * @param playerName The name of the player.
     * @return An Optional containing the server name if the player is online, otherwise empty.
     */
    public Optional<String> getOnlinePlayerServer(String playerName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String server = jedis.hget("online_players", playerName);
            return Optional.ofNullable(server);
        }
    }

    /**
     * Update the current server's MSPT value in Redis.
     *
     * @param serverName the name of the server
     * @param mspt       the average milliseconds per tick (MSPT)
     */
    public void updateServerMSPT(String serverName, double mspt) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("server_mspt", serverName, String.format("%.2f", mspt));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update MSPT for server: " + serverName, e);
        }
    }

    /**
     * Get the current MSPT value of a server from Redis.
     *
     * @param serverName the name of the server
     * @return the MSPT value if available, or -1 if not found or invalid
     */
    public double getServerMSPT(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget("server_mspt", serverName);
            if (value != null) {
                return Double.parseDouble(value);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get MSPT for server: " + serverName, e);
        }
        return -1;
    }

    /**
     * Atomically increments the round-robin counter and returns the new value.
     *
     * @return The incremented value, or -1 if an error occurs.
     */
    public long getRoundRobinCounter() {
        try (Jedis jedis = redisHandler.getJedis()) {
            long value = jedis.incr("round_robin_counter");
            if (value >= 1_000_000_000) {
                jedis.set("round_robin_counter", "0");
                value = 0;
            }
            return value;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to increment round-robin counter");
            return -1;
        }
    }
}