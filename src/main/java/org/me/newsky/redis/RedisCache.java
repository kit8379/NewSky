package org.me.newsky.redis;

import org.me.newsky.NewSky;
import org.me.newsky.model.Invitation;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RedisCache {
    private final RedisHandler redisHandler;
    private final NewSky plugin;
    private static final String ISLAND_OP_LOCK_PREFIX = "island_op_lock:";
    private static final String LUA_RELEASE_LOCK = "if redis.call('GET', KEYS[1]) == ARGV[1] then " + " return redis.call('DEL', KEYS[1]) " + "else " + " return 0 " + "end";
    private static final String LUA_EXTEND_LOCK = "if redis.call('GET', KEYS[1]) == ARGV[1] then " + " return redis.call('PEXPIRE', KEYS[1], ARGV[2]) " + "else " + " return 0 " + "end";

    public RedisCache(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }

    private String islandOpLockKey(UUID islandUuid) {
        return ISLAND_OP_LOCK_PREFIX + islandUuid;
    }

    // ========================= Island Operation Locking =========================

    /**
     * Try to acquire lock for island operations (load/unload/delete). * * @return token if acquired, empty if already locked
     */
    public Optional<String> tryAcquireIslandOpLock(UUID islandUuid, String token, long ttlMillis) {
        String key = islandOpLockKey(islandUuid);
        try (Jedis jedis = redisHandler.getJedis()) {
            String result = jedis.set(key, token, redis.clients.jedis.params.SetParams.setParams().nx().px(ttlMillis));
            if ("OK".equalsIgnoreCase(result)) {
                return Optional.of(token);
            }
            return Optional.empty();
        } catch (Exception e) {
            plugin.severe("Failed to acquire island op lock for: " + islandUuid, e);
            return Optional.empty();
        }
    }

    /**
     * Extend lock TTL if still owned by token. * * @return true if extended, false otherwise
     */
    public boolean extendIslandOpLock(UUID islandUuid, String token, long ttlMillis) {
        String key = islandOpLockKey(islandUuid);
        try (Jedis jedis = redisHandler.getJedis()) {
            Object res = jedis.eval(LUA_EXTEND_LOCK, 1, key, token, String.valueOf(ttlMillis));
            if (res instanceof Long) {
                return ((Long) res) > 0;
            }
            return false;
        } catch (Exception e) {
            plugin.severe("Failed to extend island op lock for: " + islandUuid, e);
            return false;
        }
    }

    /**
     * Release lock only if still owned by token.
     */
    public void releaseIslandOpLock(UUID islandUuid, String token) {
        String key = islandOpLockKey(islandUuid);
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.eval(LUA_RELEASE_LOCK, 1, key, token);
        } catch (Exception e) {
            plugin.severe("Failed to release island op lock for: " + islandUuid, e);
        }
    }

    /**
     * Check if island is currently operation locked.
     */
    public boolean isIslandOpLocked(UUID islandUuid) {
        String key = islandOpLockKey(islandUuid);
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.exists(key);
        } catch (Exception e) {
            plugin.severe("Failed to check island op lock for: " + islandUuid, e);
            return false;
        }
    }

    /**
     * Get remaining TTL of island operation lock.
     */
    public long getIslandOpLockTtlMillis(UUID islandUuid) {
        String key = islandOpLockKey(islandUuid);
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.pttl(key);
        } catch (Exception e) {
            plugin.severe("Failed to get island op lock TTL for: " + islandUuid, e);
            return -1;
        }
    }

    // ======================== Heartbeats and Active Servers ========================

    public void updateActiveServer(String serverName, boolean lobby) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String timestamp = String.valueOf(System.currentTimeMillis());
            jedis.hset("server_heartbeats", serverName, timestamp);
            if (lobby) {
                jedis.hdel("active_game_servers", serverName);
            } else {
                jedis.hset("active_game_servers", serverName, timestamp);
            }
        } catch (Exception e) {
            plugin.severe("Failed to update active server for: " + serverName, e);
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
                    jedis.hdel("uuid_to_name", entry.getKey());
                }
            }
            plugin.debug("RedisCache", "Cleaned up all data for server: " + serverName);
        } catch (Exception e) {
            plugin.severe("Failed to remove active server: " + serverName, e);
        }
    }

    public Map<String, String> getActiveServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hgetAll("server_heartbeats");
        } catch (Exception e) {
            plugin.severe("Failed to get active servers", e);
            return Map.of();
        }
    }

    public Map<String, String> getActiveGameServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hgetAll("active_game_servers");
        } catch (Exception e) {
            plugin.severe("Failed to get active game servers", e);
            return Map.of();
        }
    }

    // ======================== Island Loaded Servers =========================

    public void updateIslandLoadedServer(UUID islandUuid, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("island_server", islandUuid.toString(), serverName);
        } catch (Exception e) {
            plugin.severe("Failed to update island loaded server for: " + islandUuid, e);
        }
    }

    public void removeIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("island_server", islandUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to remove island loaded server for: " + islandUuid, e);
        }
    }

    public Optional<String> getIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String server = jedis.hget("island_server", islandUuid.toString());
            return Optional.ofNullable(server);
        } catch (Exception e) {
            plugin.severe("Failed to get island loaded server for: " + islandUuid, e);
            return Optional.empty();
        }
    }

    // ======================== Online Players =========================

    public Optional<String> getPlayerOnlineServer(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String server = jedis.hget("online_players", playerUuid.toString());
            return Optional.ofNullable(server);
        } catch (Exception e) {
            plugin.severe("Failed to get player server for: " + playerUuid, e);
            return Optional.empty();
        }
    }

    public void addOnlinePlayer(UUID playerUuid, String playerName, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("online_players", playerUuid.toString(), serverName);
            jedis.hset("uuid_to_name", playerUuid.toString(), playerName);
        } catch (Exception e) {
            plugin.severe("Failed to add online player: " + playerUuid, e);
        }
    }

    public void removeOnlinePlayer(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel("online_players", playerUuid.toString());
            jedis.hdel("uuid_to_name", playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to remove online player: " + playerUuid, e);
        }
    }

    public Set<UUID> getOnlinePlayersUUIDs() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys("online_players").stream().map(key -> {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    plugin.severe("Invalid UUID in online_players: " + key, e);
                    return null;
                }
            }).filter(uuid -> uuid != null).collect(Collectors.toSet());
        } catch (Exception e) {
            plugin.severe("Failed to get online players", e);
            return Set.of();
        }
    }

    public Set<String> getOnlinePlayersNames() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Set.copyOf(jedis.hvals("uuid_to_name"));
        } catch (Exception e) {
            plugin.severe("Failed to get online player names", e);
            return Set.of();
        }
    }

    // ======================== Server MSPT and Round-Robin =========================

    public void updateServerMSPT(String serverName, double mspt) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset("server_mspt", serverName, String.format("%.2f", mspt));
        } catch (Exception e) {
            plugin.severe("Failed to update MSPT for server: " + serverName, e);
        }
    }

    public double getServerMSPT(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget("server_mspt", serverName);
            if (value != null) {
                return Double.parseDouble(value);
            }
        } catch (Exception e) {
            plugin.severe("Failed to get MSPT for server: " + serverName, e);
        }
        return -1;
    }

    public long getRoundRobinCounter() {
        try (Jedis jedis = redisHandler.getJedis()) {
            long value = jedis.incr("round_robin_counter");
            if (value >= 1_000_000_000) {
                jedis.set("round_robin_counter", "0");
                value = 0;
            }
            return value;
        } catch (Exception e) {
            plugin.severe("Failed to increment round-robin counter", e);
            return -1;
        }
    }

    // ======================== Island Invitations =========================

    public void addIslandInvite(UUID inviteeUuid, UUID islandUuid, UUID inviterUuid, int ttlSeconds) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = islandUuid + ":" + inviterUuid;
            jedis.setex("island:invite:" + inviteeUuid, ttlSeconds, value);
        } catch (Exception e) {
            plugin.severe("Failed to add island invite for: " + inviteeUuid, e);
        }
    }

    public void removeIslandInvite(UUID inviteeUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del("island:invite:" + inviteeUuid);
        } catch (Exception e) {
            plugin.severe("Failed to remove island invite for: " + inviteeUuid, e);
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
            plugin.severe("Failed to get island invite for: " + inviteeUuid, e);
        }
        return Optional.empty();
    }
}