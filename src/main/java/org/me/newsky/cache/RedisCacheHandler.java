package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.database.model.*;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.logging.Level;

public class RedisCacheHandler extends CacheHandler {

    private final NewSky plugin;
    private final RedisHandler redisHandler;
    private final DatabaseHandler databaseHandler;

    public RedisCacheHandler(NewSky plugin, RedisHandler redisHandler, DatabaseHandler databaseHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void flushAllData() {
        if (getActiveServers().isEmpty()) {
            try (Jedis jedis = redisHandler.getJedis()) {
                jedis.keys("island_bans:*").forEach(jedis::del);
                jedis.del("island_levels");
                jedis.keys("island_warps:*").forEach(jedis::del);
                jedis.keys("island_homes:*").forEach(jedis::del);
                jedis.keys("island_players:*").forEach(jedis::del);
                jedis.keys("island_data:*").forEach(jedis::del);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error while flushing all data from Redis", e);
            }
        }
    }

    @Override
    protected void cacheIslandData() {
        try (Jedis jedis = redisHandler.getJedis()) {
            for (IslandData data : databaseHandler.selectAllIslandData()) {
                Map<String, String> islandData = new HashMap<>();
                islandData.put("lock", String.valueOf(data.isLock()));
                jedis.hmset("island_data:" + data.getIslandUuid(), islandData);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while caching island data to Redis", e);
        }
    }


    @Override
    protected void cacheIslandPlayers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            for (IslandPlayer player : databaseHandler.selectAllIslandPlayers()) {
                Map<String, String> playerData = new HashMap<>();
                playerData.put("role", player.getRole());
                jedis.hmset("island_players:" + player.getIslandUuid() + ":" + player.getPlayerUuid(), playerData);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while caching island players to Redis", e);
        }
    }


    @Override
    protected void cacheIslandHomes() {
        try (Jedis jedis = redisHandler.getJedis()) {
            for (IslandHome home : databaseHandler.selectAllIslandHomes()) {
                jedis.hset("island_homes:" + home.getIslandUuid() + ":" + home.getPlayerUuid(), home.getHomeName(), home.getHomeLocation());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while caching island homes to Redis", e);
        }
    }


    @Override
    protected void cacheIslandWarps() {
        try (Jedis jedis = redisHandler.getJedis()) {
            for (IslandWarp warp : databaseHandler.selectAllIslandWarps()) {
                jedis.hset("island_warps:" + warp.getIslandUuid() + ":" + warp.getPlayerUuid(), warp.getWarpName(), warp.getWarpLocation());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while caching island warps to Redis", e);
        }
    }


    @Override
    protected void cacheIslandLevels() {
        try (Jedis jedis = redisHandler.getJedis()) {
            for (IslandLevel level : databaseHandler.selectAllIslandLevels()) {
                jedis.hset("island_levels", level.getIslandUuid().toString(), String.valueOf(level.getLevel()));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while caching island levels to Redis", e);
        }
    }

    @Override
    protected void cacheIslandBans() {
        try (Jedis jedis = redisHandler.getJedis()) {
            for (IslandBan ban : databaseHandler.selectAllIslandBans()) {
                jedis.sadd("island_bans:" + ban.getIslandUuid().toString(), ban.getBannedPlayer().toString());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while caching island bans to Redis", e);
        }
    }

    @Override
    public void createIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> islandData = new HashMap<>();
            islandData.put("lock", "false");
            islandData.put("pvp", "false");
            jedis.hmset("island_data:" + islandUuid.toString(), islandData);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while creating island in Redis", e);
        }
        databaseHandler.addIslandData(islandUuid);
    }

    @Override
    public void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> playerData = new HashMap<>();
            playerData.put("role", role);
            jedis.hmset("island_players:" + islandUuid + ":" + playerUuid, playerData);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating island player", e);
        }
        databaseHandler.updateIslandPlayer(islandUuid, playerUuid, role);
    }

    @Override
    public void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_homes:" + islandUuid + ":" + playerUuid, homeName, homeLocation);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating home point", e);
        }
        databaseHandler.updateHomePoint(islandUuid, playerUuid, homeName, homeLocation);
    }

    @Override
    public void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_warps:" + islandUuid + ":" + playerUuid, warpName, warpLocation);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating warp point", e);
        }
        databaseHandler.updateWarpPoint(islandUuid, playerUuid, warpName, warpLocation);
    }

    @Override
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
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating island owner", e);
        }
        databaseHandler.updateIslandOwner(islandUuid, playerUuid);
    }

    @Override
    public void updateIslandLock(UUID islandUuid, boolean lock) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_data:" + islandUuid.toString(), "lock", String.valueOf(lock));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating island lock status", e);
        }
        databaseHandler.updateIslandLock(islandUuid, lock);
    }

    @Override
    public void updateIslandPvp(UUID islandUuid, boolean pvp) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_data:" + islandUuid.toString(), "pvp", String.valueOf(pvp));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating island pvp status", e);
        }
        databaseHandler.updateIslandPvp(islandUuid, pvp);
    }

    @Override
    public void updateIslandLevel(UUID islandUuid, int level) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_levels", islandUuid.toString(), String.valueOf(level));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating island level", e);
        }
        databaseHandler.updateIslandLevel(islandUuid, level);
    }

    @Override
    public void updateBanPlayer(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.sadd("island_bans:" + islandUuid.toString(), playerUuid.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating ban player", e);
        }
        databaseHandler.updateBanPlayer(islandUuid, playerUuid);
    }

    @Override
    public void deleteIsland(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island_bans:" + islandUuid.toString());
            jedis.hdel("island_levels", islandUuid.toString());
            jedis.keys("island_warps:" + islandUuid + ":*").forEach(jedis::del);
            jedis.keys("island_homes:" + islandUuid + ":*").forEach(jedis::del);
            jedis.keys("island_players:" + islandUuid + ":*").forEach(jedis::del);
            jedis.del("island_data:" + islandUuid);
            databaseHandler.deleteIsland(islandUuid);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while deleting island", e);
        }
    }

    @Override
    public void deleteIslandPlayer(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island_players:" + islandUuid + ":" + playerUuid);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while deleting island player", e);
        }
        databaseHandler.deleteIslandPlayer(islandUuid, playerUuid);
    }

    @Override
    public void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_homes:" + islandUuid + ":" + playerUuid, homeName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while deleting home point", e);
        }
        databaseHandler.deleteHomePoint(islandUuid, playerUuid, homeName);
    }

    @Override
    public void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_warps:" + islandUuid + ":" + playerUuid, warpName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while deleting warp point", e);
        }
        databaseHandler.deleteWarpPoint(islandUuid, playerUuid, warpName);
    }

    @Override
    public void deleteBanPlayer(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.srem("island_bans:" + islandUuid.toString(), playerUuid.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while deleting ban player", e);
        }
        databaseHandler.deleteBanPlayer(islandUuid, playerUuid);
    }

    @Override
    public boolean getIslandLock(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String lock = jedis.hget("island_data:" + islandUuid.toString(), "lock");
            return Boolean.parseBoolean(lock);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island lock status", e);
            return false;
        }
    }

    @Override
    public boolean getIslandPvp(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String pvp = jedis.hget("island_data:" + islandUuid.toString(), "pvp");
            return Boolean.parseBoolean(pvp);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island pvp status", e);
            return false;
        }
    }

    @Override
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

    @Override
    public Set<String> getHomeNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String key = "island_homes:" + islandUuid + ":" + playerUuid;
            return jedis.hkeys(key);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting home names", e);
            return Collections.emptySet();
        }
    }

    @Override
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

    @Override
    public Set<String> getWarpNames(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String key = "island_warps:" + islandUuid + ":" + playerUuid;
            return jedis.hkeys(key);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting warp names", e);
            return Collections.emptySet();
        }
    }

    @Override
    public int getIslandLevel(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String level = jedis.hget("island_levels", islandUuid.toString());
            return level != null ? Integer.parseInt(level) : 0;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island level", e);
            return 0;
        }
    }

    @Override
    public Map<UUID, Integer> getTopIslandLevels(int size) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Map<String, String> islandLevels = jedis.hgetAll("island_levels");
            return islandLevels.entrySet().stream().map(entry -> new AbstractMap.SimpleEntry<>(UUID.fromString(entry.getKey()), Integer.parseInt(entry.getValue()))).sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(size).collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting top island levels", e);
            return Collections.emptyMap();
        }
    }

    @Override
    public boolean getPlayerBanned(UUID islandUuid, UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.sismember("island_bans:" + islandUuid.toString(), playerUuid.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while checking if player is banned", e);
            return false;
        }
    }

    @Override
    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> bannedPlayers = jedis.smembers("island_bans:" + islandUuid.toString());
            Set<UUID> bannedPlayerUuids = new HashSet<>();
            for (String bannedPlayer : bannedPlayers) {
                bannedPlayerUuids.add(UUID.fromString(bannedPlayer));
            }
            return bannedPlayerUuids;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting banned players", e);
            return Collections.emptySet();
        }
    }

    @Override
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
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island owner", e);
        }
        return null;
    }

    @Override
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
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island members", e);
        }
        return members;
    }

    @Override
    public Set<UUID> getIslandPlayers(UUID islandUuid) {
        Set<UUID> members = new HashSet<>();
        try (Jedis jedis = redisHandler.getJedis()) {
            Set<String> keys = jedis.keys("island_players:" + islandUuid.toString() + ":*");
            for (String key : keys) {
                String[] parts = key.split(":");
                members.add(UUID.fromString(parts[2]));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island players", e);
        }
        return members;
    }

    @Override
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
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island UUID for player", e);
            return Optional.empty();
        }
    }

    @Override
    public void updateActiveServer(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("server_heartbeats", serverName, String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating active server", e);
        }
    }

    @Override
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
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while removing active server", e);
        }
    }

    @Override
    public Map<String, String> getActiveServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hgetAll("server_heartbeats");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting active servers", e);
            return Collections.emptyMap();
        }
    }

    @Override
    public void updateIslandLoadedServer(UUID islandUuid, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_server", islandUuid.toString(), serverName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while updating island loaded server", e);
        }
    }

    @Override
    public void removeIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_server", islandUuid.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while removing island loaded server", e);
        }
    }

    @Override
    public String getIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hget("island_server", islandUuid.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting island loaded server", e);
            return null;
        }
    }

    @Override
    public void addOnlinePlayer(String playerName, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("online_players", playerName, serverName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while adding online player", e);
        }
    }

    @Override
    public void removeOnlinePlayer(String playerName, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("online_players", playerName, serverName);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while removing online player", e);
        }
    }

    @Override
    public Set<String> getOnlinePlayers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys("online_players");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error while getting online players", e);
            return Collections.emptySet();
        }
    }
}