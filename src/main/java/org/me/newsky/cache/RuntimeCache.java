package org.me.newsky.cache;

import org.me.newsky.NewSky;
import org.me.newsky.model.Invitation;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.*;
import java.util.stream.Collectors;

public class RuntimeCache {

    /*
     * ====================================================================================================
     * =                                            PREFIXES                                               =
     * ====================================================================================================
     */

    private static final String RUNTIME_PREFIX = "newsky:runtime:";

    private static final String ISLAND_OP_LOCK_PREFIX = RUNTIME_PREFIX + "lock:island_op:";

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

    /*
     * ====================================================================================================
     * =                                        INTERNAL HELPERS                                           =
     * ====================================================================================================
     */

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
        } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

        return keys;
    }

    private Map<String, String> getHeartbeatMap(Jedis jedis, String prefix) {
        List<String> keys = scanKeys(jedis, prefix + "*");
        if (keys.isEmpty()) {
            return Map.of();
        }

        List<String> values = jedis.mget(keys.toArray(new String[0]));
        Map<String, String> result = new LinkedHashMap<>(keys.size());

        for (int i = 0; i < keys.size(); i++) {
            String value = values.get(i);
            if (value == null) {
                continue;
            }

            String serverName = extractSuffix(keys.get(i), prefix);
            result.put(serverName, value);
        }

        return result;
    }

    /*
     * ====================================================================================================
     * =                                ISLAND OPERATION DISTRIBUTED LOCK                                  =
     * ====================================================================================================
     * This section is used to coordinate island-sensitive operations across multiple servers.
     * Typical use cases:
     * - island load
     * - island unload
     * - island delete
     * - any operation that must not run concurrently for the same island
     * ====================================================================================================
     */

    public Optional<String> tryAcquireIslandOpLock(UUID islandUuid, String token, long ttlMillis) {
        String key = islandOpLockKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            String result = jedis.set(key, token, SetParams.setParams().nx().px(ttlMillis));
            return "OK".equalsIgnoreCase(result) ? Optional.of(token) : Optional.empty();
        } catch (Exception e) {
            plugin.severe("Failed to acquire island op lock for: " + islandUuid, e);
            return Optional.empty();
        }
    }

    public boolean extendIslandOpLock(UUID islandUuid, String token, long ttlMillis) {
        String key = islandOpLockKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            Object result = jedis.eval(LUA_EXTEND_LOCK, 1, key, token, String.valueOf(ttlMillis));
            return result instanceof Long && ((Long) result) > 0;
        } catch (Exception e) {
            plugin.severe("Failed to extend island op lock for: " + islandUuid, e);
            return false;
        }
    }

    public void releaseIslandOpLock(UUID islandUuid, String token) {
        String key = islandOpLockKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.eval(LUA_RELEASE_LOCK, 1, key, token);
        } catch (Exception e) {
            plugin.severe("Failed to release island op lock for: " + islandUuid, e);
        }
    }

    public boolean isIslandOpLocked(UUID islandUuid) {
        String key = islandOpLockKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.exists(key);
        } catch (Exception e) {
            plugin.severe("Failed to check island op lock for: " + islandUuid, e);
            return false;
        }
    }

    public long getIslandOpLockTtlMillis(UUID islandUuid) {
        String key = islandOpLockKey(islandUuid);

        try (Jedis jedis = redisHandler.getJedis()) {
            return jedis.pttl(key);
        } catch (Exception e) {
            plugin.severe("Failed to get island op lock TTL for: " + islandUuid, e);
            return -1;
        }
    }

    /*
     * ====================================================================================================
     * =                                   SERVER HEARTBEAT / LIVENESS                                     =
     * ====================================================================================================
     * This section tracks currently alive servers using per-server TTL keys.
     *
     * All servers:
     *   newsky:runtime:heartbeat:server:<serverName>
     *
     * Non-lobby game servers:
     *   newsky:runtime:heartbeat:game_server:<serverName>
     *
     * If a key expires naturally, that server is treated as offline/stale.
     * ====================================================================================================
     */

    public void updateActiveServer(String serverName, boolean lobby, int ttlSeconds) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        try (Jedis jedis = redisHandler.getJedis()) {
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

    public void removeActiveServer(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();

            pipeline.del(serverHeartbeatKey(serverName));
            pipeline.del(gameServerHeartbeatKey(serverName));
            pipeline.hdel(SERVER_MSPT_KEY, serverName);
            pipeline.sync();

            Map<String, String> islandServerMap = jedis.hgetAll(ISLAND_SERVER_KEY);
            if (!islandServerMap.isEmpty()) {
                Pipeline islandPipeline = jedis.pipelined();
                for (Map.Entry<String, String> entry : islandServerMap.entrySet()) {
                    if (serverName.equals(entry.getValue())) {
                        islandPipeline.hdel(ISLAND_SERVER_KEY, entry.getKey());
                    }
                }
                islandPipeline.sync();
            }

            Map<String, String> onlinePlayers = jedis.hgetAll(ONLINE_PLAYERS_KEY);
            if (!onlinePlayers.isEmpty()) {
                Pipeline playerPipeline = jedis.pipelined();
                for (Map.Entry<String, String> entry : onlinePlayers.entrySet()) {
                    if (serverName.equals(entry.getValue())) {
                        playerPipeline.hdel(ONLINE_PLAYERS_KEY, entry.getKey());
                        playerPipeline.hdel(ONLINE_PLAYER_NAMES_KEY, entry.getKey());
                    }
                }
                playerPipeline.sync();
            }

            plugin.debug("RuntimeCache", "Cleaned up all runtime data for server: " + serverName);
        } catch (Exception e) {
            plugin.severe("Failed to remove active server: " + serverName, e);
        }
    }

    public Map<String, String> getActiveServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return getHeartbeatMap(jedis, SERVER_HEARTBEAT_PREFIX);
        } catch (Exception e) {
            plugin.severe("Failed to get active servers", e);
            return Map.of();
        }
    }

    public Map<String, String> getActiveGameServers() {
        try (Jedis jedis = redisHandler.getJedis()) {
            return getHeartbeatMap(jedis, GAME_SERVER_HEARTBEAT_PREFIX);
        } catch (Exception e) {
            plugin.severe("Failed to get active game servers", e);
            return Map.of();
        }
    }

    /*
     * ====================================================================================================
     * =                                  ISLAND -> LOADED SERVER ROUTING                                  =
     * ====================================================================================================
     * This section stores which server currently owns / has loaded a specific island.
     * It is used for routing, lookup, and cross-server coordination.
     * ====================================================================================================
     */

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
            return Optional.ofNullable(jedis.hget(ISLAND_SERVER_KEY, islandUuid.toString()));
        } catch (Exception e) {
            plugin.severe("Failed to get island loaded server for: " + islandUuid, e);
            return Optional.empty();
        }
    }

    /*
     * ====================================================================================================
     * =                                       ONLINE PLAYER PRESENCE                                      =
     * ====================================================================================================
     * This section tracks online players and which server they are on.
     * Two hashes are used:
     *
     * 1) UUID -> server
     * 2) UUID -> player name
     *
     * This allows fast lookup by UUID while still being able to list names.
     * ====================================================================================================
     */

    public Optional<String> getPlayerOnlineServer(UUID playerUuid) {
        try (Jedis jedis = redisHandler.getJedis()) {
            return Optional.ofNullable(jedis.hget(ONLINE_PLAYERS_KEY, playerUuid.toString()));
        } catch (Exception e) {
            plugin.severe("Failed to get player server for: " + playerUuid, e);
            return Optional.empty();
        }
    }

    public void addOnlinePlayer(UUID playerUuid, String playerName, String serverName) {
        String playerUuidString = playerUuid.toString();

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            pipeline.hset(ONLINE_PLAYERS_KEY, playerUuidString, serverName);
            pipeline.hset(ONLINE_PLAYER_NAMES_KEY, playerUuidString, playerName);
            pipeline.sync();
        } catch (Exception e) {
            plugin.severe("Failed to add online player: " + playerUuid, e);
        }
    }

    public void removeOnlinePlayer(UUID playerUuid) {
        String playerUuidString = playerUuid.toString();

        try (Jedis jedis = redisHandler.getJedis()) {
            Pipeline pipeline = jedis.pipelined();
            pipeline.hdel(ONLINE_PLAYERS_KEY, playerUuidString);
            pipeline.hdel(ONLINE_PLAYER_NAMES_KEY, playerUuidString);
            pipeline.sync();
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
            List<String> values = jedis.hvals(ONLINE_PLAYER_NAMES_KEY);
            return values.isEmpty() ? Set.of() : Set.copyOf(values);
        } catch (Exception e) {
            plugin.severe("Failed to get online player names", e);
            return Set.of();
        }
    }

    /*
     * ====================================================================================================
     * =                                  SERVER METRICS / LOAD BALANCING                                  =
     * ====================================================================================================
     * This section stores lightweight server metrics used for balancing or routing decisions.
     * Current data:
     * - MSPT per server
     * - round-robin counter
     * ====================================================================================================
     */

    public void updateServerMSPT(String serverName, double mspt) {
        try (Jedis jedis = redisHandler.getJedis()) {
            jedis.hset(SERVER_MSPT_KEY, serverName, String.format(Locale.ROOT, "%.2f", mspt));
        } catch (Exception e) {
            plugin.severe("Failed to update MSPT for server: " + serverName, e);
        }
    }

    public double getServerMSPT(String serverName) {
        try (Jedis jedis = redisHandler.getJedis()) {
            String value = jedis.hget(SERVER_MSPT_KEY, serverName);
            return value != null ? Double.parseDouble(value) : -1;
        } catch (Exception e) {
            plugin.severe("Failed to get MSPT for server: " + serverName, e);
            return -1;
        }
    }

    public long getRoundRobinCounter() {
        try (Jedis jedis = redisHandler.getJedis()) {
            long value = jedis.incr(ROUND_ROBIN_COUNTER_KEY);

            if (value >= 1_000_000_000L) {
                jedis.set(ROUND_ROBIN_COUNTER_KEY, "0");
                return 0;
            }

            return value;
        } catch (Exception e) {
            plugin.severe("Failed to increment round-robin counter", e);
            return -1;
        }
    }

    /*
     * ====================================================================================================
     * =                                     TEMPORARY ISLAND INVITATIONS                                  =
     * ====================================================================================================
     * This section stores temporary invitation state with Redis TTL.
     *
     * Key:
     *   newsky:runtime:invite:island:<inviteeUuid>
     *
     * Value format:
     *   <islandUuid>:<inviterUuid>
     *
     * Expiry is controlled by Redis TTL.
     * ====================================================================================================
     */

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
            if (value == null || value.isEmpty()) {
                return Optional.empty();
            }

            String[] parts = value.split(":");
            if (parts.length != 2) {
                return Optional.empty();
            }

            UUID islandUuid = UUID.fromString(parts[0]);
            UUID inviterUuid = UUID.fromString(parts[1]);
            return Optional.of(new Invitation(islandUuid, inviterUuid));
        } catch (Exception e) {
            plugin.severe("Failed to get island invite for: " + inviteeUuid, e);
            return Optional.empty();
        }
    }
}