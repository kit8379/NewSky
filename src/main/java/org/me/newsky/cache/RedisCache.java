package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.model.Invitation;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class RedisCache {

    private final RedisHandler redisHandler;
    private final NewSky plugin;

    public RedisCache(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }

    // Server heartbeats
    public void updateActiveServer(String serverName, boolean lobby) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            jedis.hset("server_heartbeats", serverName, timestamp);
            if (!lobby) {
                jedis.hset("active_game_servers", serverName, timestamp);
            }
        }
    }

    public void removeActiveServer(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("server_heartbeats", serverName);
            jedis.hdel("active_game_servers", serverName);

            Map<String, String> islandServerMap = jedis.hgetAll("island_server");
            for (Map.Entry<String, String> entry : islandServerMap.entrySet()) {
                if (entry.getValue().equals(serverName)) {
                    jedis.hdel("island_server", entry.getKey());
                }
            }

            Map<String, String> onlinePlayers = jedis.hgetAll("online_players");
            for (Map.Entry<String, String> entry : onlinePlayers.entrySet()) {
                if (entry.getValue().equals(serverName)) {
                    jedis.hdel("online_players", entry.getKey());
                }
            }
        }
    }

    public Map<String, String> getActiveServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hgetAll("server_heartbeats");
        }
    }

    public Map<String, String> getActiveGameServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hgetAll("active_game_servers");
        }
    }

    // Island loaded server
    public void updateIslandLoadedServer(UUID islandUuid, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_server", islandUuid.toString(), serverName);
        }
    }

    public void removeIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_server", islandUuid.toString());
        }
    }

    public Optional<String> getIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String server = jedis.hget("island_server", islandUuid.toString());
            return Optional.ofNullable(server);
        }
    }

    // Global Online players
    public void addOnlinePlayer(String playerName, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("online_players", playerName, serverName);
        }
    }

    public void removeOnlinePlayer(String playerName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("online_players", playerName);
        }
    }

    public Set<String> getOnlinePlayers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys("online_players");
        }
    }

    public Optional<String> getOnlinePlayerServer(String playerName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String server = jedis.hget("online_players", playerName);
            return Optional.ofNullable(server);
        }
    }

    // Server MSPT
    public void updateServerMSPT(String serverName, double mspt) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("server_mspt", serverName, String.format("%.2f", mspt));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update MSPT for server: " + serverName, e);
        }
    }

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

    // Round-robin counter
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

    // Island Invitation
    public void addIslandInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = islandUuid + ":" + inviterUuid;
            jedis.setex("island:invite:" + inviteeUuid, ttlSeconds, value);
        }
    }

    public void removeIslandInvite(UUID inviteeUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island:invite:" + inviteeUuid);
        }
    }

    public Optional<Invitation> getIslandInvite(UUID inviteeUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.get("island:invite:" + inviteeUuid);
            if (value != null) {
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    UUID islandUuid = UUID.fromString(parts[0]);
                    UUID inviterUuid = UUID.fromString(parts[1]);
                    return Optional.of(new Invitation(islandUuid, inviterUuid));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get island invite for: " + inviteeUuid, e);
        }
        return Optional.empty();
    }
}