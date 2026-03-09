package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.model.Invitation;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;
import java.util.stream.Collectors;

public class RuntimeCache {

    private static final String RUNTIME_PREFIX = "newsky:runtime:";

    private static final String ISLAND_OP_LOCK_PREFIX = RUNTIME_PREFIX + "lock:island_op:";

    // Heartbeat keys now use per-server TTL keys instead of shared hash timestamps.
    private static final String SERVER_HEARTBEAT_PREFIX = RUNTIME_PREFIX + "heartbeat:server:";
    private static final String GAME_SERVER_HEARTBEAT_PREFIX = RUNTIME_PREFIX + "heartbeat:game_server:";

    private static final String ISLAND_SERVER_KEY = RUNTIME_PREFIX + "island_server";
    private static final String ONLINE_PLAYERS_KEY = RUNTIME_PREFIX + "online_players";
    private static final String ONLINE_PLAYER_NAMES_KEY = RUNTIME_PREFIX + "online_player_names";
    private static final String SERVER_MSPT_KEY = RUNTIME_PREFIX + "server_mspt";
    private static final String ROUND_ROBIN_COUNTER_KEY = RUNTIME_PREFIX + "round_robin_counter";
    private static final String ISLAND_INVITE_PREFIX = RUNTIME_PREFIX + "invite:island:";

    private static final String LUA_RELEASE_LOCK = "if redis.call('GET', KEYS[1]) == ARGV[1] then " + " return redis.call('DEL', KEYS[1]) " + "else " + " return 0 " + "end";

    private static final String LUA_EXTEND_LOCK = "if redis.call('GET', KEYS[1]) == ARGV[1] then " + " return redis.call('PEXPIRE', KEYS[1], ARGV[2]) " + "else " + " return 0 " + "end";

    private final RedisHandler redisHandler;
    private final NewSky plugin;

    public RuntimeCache(NewSky plugin, RedisHandler redisHandler) {
        this.plugin = plugin;
        this.redisHandler = redisHandler;
    }

    private String islandOpLockKey(UUID islandUuid) {
        return ISLAND_OP_LOCK_PREFIX + islandUuid;
    }

    private String islandInviteKey(UUID inviteeUuid) {
        return ISLAND_INVITE_PREFIX + inviteeUuid;
    }

    private String serverHeartbeatKey(String serverName) {
        return SERVER_HEARTBEAT_PREFIX + serverName;
    }

    private String gameServerHeartbeatKey(String serverName) {
        return GAME_SERVER_HEARTBEAT_PREFIX + serverName;
    }

    private String extractSuffix(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) {
            return key;
        }
        return key.substring(prefix.length());
    }

    private List<String> scanKeys(Jedis jedis, String pattern) {
        List<String> keys = new ArrayList<>();
        String cursor = ScanParams.SCAN_POINTER_START;
        ScanParams params = new ScanParams().match(pattern).count(200);

        do {
            ScanResult<String> result = jedis.scan(cursor, params);
            keys.addAll(result.getResult());
            cursor = result.getCursor();
        } while (!"0".equals(cursor));

        return keys;
    }

    // ========================= Island Operation Locking =========================

    /**
     * Try to acquire lock for island operations (load/unload/delete).
     *
     * @return token if acquired, empty if already locked
     */
    public Optional<String> tryAcquireIslandOpLock(UUID islandUuid, String token, long ttlMillis) {
        String key = islandOpLockKey(islandUuid);
        try (Jedis jedis = redisHandler.getJedis()) {
            String result = jedis.set(key, token, SetParams.setParams().nx().px(ttlMillis));
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
     * Extend lock TTL if still owned by token.
     *
     * @return true if extended, false otherwise
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

    /**
     * Update heartbeat using TTL keys.
     * <p>
     * All servers get:
     * newsky:runtime:heartbeat:server:<serverName>
     * <p>
     * Non-lobby/game servers also get:
     * newsky:runtime:heartbeat:game_server:<serverName>
     */
    public void updateActiveServer(String serverName, boolean lobby, int ttlSeconds) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String timestamp = String.valueOf(System.currentTimeMillis());

            jedis.setex(serverHeartbeatKey(serverName), ttlSeconds, timestamp);

            if (lobby) {
                jedis.del(gameServerHeartbeatKey(serverName));
            } else {
                jedis.setex(gameServerHeartbeatKey(serverName), ttlSeconds, timestamp);
            }
        } catch (Exception e) {
            plugin.severe("Failed to update active server for: " + serverName, e);
        }
    }

    /**
     * Remove this server's heartbeat/runtime traces immediately on shutdown or startup cleanup.
     */
    public void removeActiveServer(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del(serverHeartbeatKey(serverName));
            jedis.del(gameServerHeartbeatKey(serverName));
            jedis.hdel(SERVER_MSPT_KEY, serverName);

            Map<String, String> islandServerMap = jedis.hgetAll(ISLAND_SERVER_KEY);
            for (Map.Entry<String, String> entry : islandServerMap.entrySet()) {
                if (entry.getValue().equals(serverName)) {
                    jedis.hdel(ISLAND_SERVER_KEY, entry.getKey());
                }
            }

            Map<String, String> onlinePlayers = jedis.hgetAll(ONLINE_PLAYERS_KEY);
            for (Map.Entry<String, String> entry : onlinePlayers.entrySet()) {
                if (entry.getValue().equals(serverName)) {
                    jedis.hdel(ONLINE_PLAYERS_KEY, entry.getKey());
                    jedis.hdel(ONLINE_PLAYER_NAMES_KEY, entry.getKey());
                }
            }

            plugin.debug("RuntimeCache", "Cleaned up all runtime data for server: " + serverName);
        } catch (Exception e) {
            plugin.severe("Failed to remove active server: " + serverName, e);
        }
    }

    /**
     * Returns currently alive servers based on TTL heartbeat keys.
     * The returned value map is:
     * serverName -> last heartbeat timestamp string
     */
    public Map<String, String> getActiveServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            List<String> keys = scanKeys(jedis, SERVER_HEARTBEAT_PREFIX + "*");
            if (keys.isEmpty()) {
                return Map.of();
            }

            List<String> values = jedis.mget(keys.toArray(new String[0]));
            Map<String, String> result = new LinkedHashMap<>();

            for (int i = 0; i < keys.size(); i++) {
                String value = values.get(i);
                if (value == null) {
                    continue;
                }
                String serverName = extractSuffix(keys.get(i), SERVER_HEARTBEAT_PREFIX);
                result.put(serverName, value);
            }

            return result;
        } catch (Exception e) {
            plugin.severe("Failed to get active servers", e);
            return Map.of();
        }
    }

    /**
     * Returns currently alive non-lobby game servers based on TTL heartbeat keys.
     * The returned value map is:
     * serverName -> last heartbeat timestamp string
     */
    public Map<String, String> getActiveGameServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            List<String> keys = scanKeys(jedis, GAME_SERVER_HEARTBEAT_PREFIX + "*");
            if (keys.isEmpty()) {
                return Map.of();
            }

            List<String> values = jedis.mget(keys.toArray(new String[0]));
            Map<String, String> result = new LinkedHashMap<>();

            for (int i = 0; i < keys.size(); i++) {
                String value = values.get(i);
                if (value == null) {
                    continue;
                }
                String serverName = extractSuffix(keys.get(i), GAME_SERVER_HEARTBEAT_PREFIX);
                result.put(serverName, value);
            }

            return result;
        } catch (Exception e) {
            plugin.severe("Failed to get active game servers", e);
            return Map.of();
        }
    }

    // ======================== Island Loaded Servers =========================

    public void updateIslandLoadedServer(UUID islandUuid, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(ISLAND_SERVER_KEY, islandUuid.toString(), serverName);
        } catch (Exception e) {
            plugin.severe("Failed to update island loaded server for: " + islandUuid, e);
        }
    }

    public void removeIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel(ISLAND_SERVER_KEY, islandUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to remove island loaded server for: " + islandUuid, e);
        }
    }

    public Optional<String> getIslandLoadedServer(UUID islandUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String server = jedis.hget(ISLAND_SERVER_KEY, islandUuid.toString());
            return Optional.ofNullable(server);
        } catch (Exception e) {
            plugin.severe("Failed to get island loaded server for: " + islandUuid, e);
            return Optional.empty();
        }
    }

    // ======================== Online Players =========================

    public Optional<String> getPlayerOnlineServer(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String server = jedis.hget(ONLINE_PLAYERS_KEY, playerUuid.toString());
            return Optional.ofNullable(server);
        } catch (Exception e) {
            plugin.severe("Failed to get player server for: " + playerUuid, e);
            return Optional.empty();
        }
    }

    public void addOnlinePlayer(UUID playerUuid, String playerName, String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(ONLINE_PLAYERS_KEY, playerUuid.toString(), serverName);
            jedis.hset(ONLINE_PLAYER_NAMES_KEY, playerUuid.toString(), playerName);
        } catch (Exception e) {
            plugin.severe("Failed to add online player: " + playerUuid, e);
        }
    }

    public void removeOnlinePlayer(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hdel(ONLINE_PLAYERS_KEY, playerUuid.toString());
            jedis.hdel(ONLINE_PLAYER_NAMES_KEY, playerUuid.toString());
        } catch (Exception e) {
            plugin.severe("Failed to remove online player: " + playerUuid, e);
        }
    }

    public Set<UUID> getOnlinePlayersUUIDs() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.hkeys(ONLINE_PLAYERS_KEY).stream().map(key -> {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException e) {
                    plugin.severe("Invalid UUID in online players key: " + key, e);
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toSet());
        } catch (Exception e) {
            plugin.severe("Failed to get online players", e);
            return Set.of();
        }
    }

    public Set<String> getOnlinePlayersNames() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Set.copyOf(jedis.hvals(ONLINE_PLAYER_NAMES_KEY));
        } catch (Exception e) {
            plugin.severe("Failed to get online player names", e);
            return Set.of();
        }
    }

    // ======================== Server MSPT and Round-Robin =========================

    public void updateServerMSPT(String serverName, double mspt) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(SERVER_MSPT_KEY, serverName, String.format("%.2f", mspt));
        } catch (Exception e) {
            plugin.severe("Failed to update MSPT for server: " + serverName, e);
        }
    }

    public double getServerMSPT(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(SERVER_MSPT_KEY, serverName);
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
            long value = jedis.incr(ROUND_ROBIN_COUNTER_KEY);
            if (value >= 1_000_000_000) {
                jedis.set(ROUND_ROBIN_COUNTER_KEY, "0");
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
            jedis.setex(islandInviteKey(inviteeUuid), ttlSeconds, value);
        } catch (Exception e) {
            plugin.severe("Failed to add island invite for: " + inviteeUuid, e);
        }
    }

    public void removeIslandInvite(UUID inviteeUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.del(islandInviteKey(inviteeUuid));
        } catch (Exception e) {
            plugin.severe("Failed to remove island invite for: " + inviteeUuid, e);
        }
    }

    public Optional<Invitation> getIslandInvite(UUID inviteeUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.get(islandInviteKey(inviteeUuid));
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