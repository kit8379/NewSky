package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tracks cluster server liveness (heartbeats), selection metrics (MSPT) and the
 * shared round-robin counter. Cleans up online-player presence when a server goes
 * away; island claims are never released by a peer (no failover), only by the
 * holder itself or its same-name restart.
 */
public class ServerRegistry extends ClusterState {

    /**
     * Increments and wraps atomically: Redis INCR errors out at the signed 64-bit
     * limit instead of wrapping, and a non-atomic check-then-SET reset would let
     * concurrent callers reset twice and skew the rotation.
     */
    private static final String ROUND_ROBIN_INCR_SCRIPT = """
            local value = redis.call('INCR', KEYS[1])
            if value >= 1000000000 then
                redis.call('SET', KEYS[1], '0')
            end
            return value
            """;

    /**
     * Clears the soft state of a server whose heartbeat has expired: online presence,
     * MSPT and known-set membership. Island claims are deliberately left alone - a
     * false positive here must never hand a live server's islands to someone else.
     * The liveness check and the deletes run as one atomic script, so a server coming
     * back mid-reap can never lose state it has just written.
     * Returns -1 when the server is alive, otherwise the number of stale entries removed.
     */
    private static final String REAP_DEAD_SERVER_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return -1
            end

            local removed = 0

            local players = redis.call('HGETALL', KEYS[2])
            for i = 1, #players, 2 do
                if players[i + 1] == ARGV[1] then
                    redis.call('HDEL', KEYS[2], players[i])
                    redis.call('HDEL', KEYS[3], players[i])
                    removed = removed + 1
                end
            end

            redis.call('HDEL', KEYS[4], ARGV[1])
            redis.call('SREM', KEYS[5], ARGV[1])
            return removed
            """;

    /**
     * Writes the heartbeat keys and reports whether the main key still existed.
     * Absence is ground truth for "the heartbeat expired since the previous beat":
     * only this server rewrites its own key, so an expiry leaves it absent until
     * the next beat, and the reaper only acts while it is absent — an existing key
     * therefore proves no peer can have reaped this server in between.
     * Known-set membership self-heals on every beat; the reaper is the only remover.
     */
    private static final String HEARTBEAT_SCRIPT = """
            local existed = redis.call('EXISTS', KEYS[1])
            redis.call('SETEX', KEYS[1], ARGV[1], ARGV[2])
            if ARGV[3] == '1' then
                redis.call('DEL', KEYS[2])
            else
                redis.call('SETEX', KEYS[2], ARGV[1], ARGV[2])
            end
            redis.call('SADD', KEYS[3], ARGV[4])
            return existed
            """;

    private final IslandRegistry islandRegistry;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public ServerRegistry(NewSky plugin, RedisHandler redisHandler, IslandRegistry islandRegistry, OnlinePlayerRegistry onlinePlayerRegistry) {
        super(plugin, redisHandler);
        this.islandRegistry = islandRegistry;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    /**
     * Returns whether the heartbeat key still existed before this beat rewrote it;
     * false means the heartbeat expired at some point since the previous beat.
     */
    public boolean updateActiveServer(String serverName, boolean lobby, int ttlSeconds) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return execute(jedis -> (Long) jedis.eval(HEARTBEAT_SCRIPT, List.of(ClusterKeys.serverHeartbeat(serverName), ClusterKeys.gameServerHeartbeat(serverName), ClusterKeys.knownServers()), List.of(String.valueOf(ttlSeconds), timestamp, lobby ? "1" : "0", serverName)) == 1L, "Failed to update active server for: " + serverName);
    }

    public Set<String> getKnownServers() {
        return execute(jedis -> jedis.smembers(ClusterKeys.knownServers()), "Failed to get known servers");
    }

    public long reapDeadServer(String serverName) {
        return execute(jedis -> (Long) jedis.eval(REAP_DEAD_SERVER_SCRIPT, List.of(ClusterKeys.serverHeartbeat(serverName), ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers(), ClusterKeys.serverMspt(), ClusterKeys.knownServers()), List.of(serverName)), "Failed to reap dead server: " + serverName);
    }

    public void removeActiveServer(String serverName) {
        run(jedis -> {
            Pipeline pipeline = jedis.pipelined();
            pipeline.del(ClusterKeys.serverHeartbeat(serverName));
            pipeline.del(ClusterKeys.gameServerHeartbeat(serverName));
            pipeline.hdel(ClusterKeys.serverMspt(), serverName);
            pipeline.sync();
        }, "Failed to remove active server: " + serverName);

        islandRegistry.removeServerMappings(serverName);
        onlinePlayerRegistry.removeAllOnServer(serverName);
        plugin.debug("ServerRegistry", "Cleaned up all state data for server: " + serverName);
    }

    public Map<String, String> getActiveServers() {
        return getActiveServersByPrefix(ClusterKeys.serverHeartbeatPrefix(), "Failed to get active servers");
    }

    public Map<String, String> getActiveGameServers() {
        return getActiveServersByPrefix(ClusterKeys.gameServerHeartbeatPrefix(), "Failed to get active game servers");
    }

    private Map<String, String> getActiveServersByPrefix(String prefix, String errorMessage) {
        return execute(jedis -> {
            Map<String, String> result = new LinkedHashMap<>();

            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(prefix + "*").count(200);

            do {
                ScanResult<String> scan = jedis.scan(cursor, params);

                for (String key : scan.getResult()) {
                    String value = jedis.get(key);
                    if (value == null) {
                        continue;
                    }

                    String serverName = key.substring(prefix.length());
                    result.put(serverName, value);
                }

                cursor = scan.getCursor();

            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));

            return result;
        }, errorMessage);
    }

    public void updateServerMSPT(String serverName, double mspt) {
        run(jedis -> jedis.hset(ClusterKeys.serverMspt(), serverName, String.format(Locale.ROOT, "%.2f", mspt)), "Failed to update MSPT for server: " + serverName);
    }

    public double getServerMSPT(String serverName) {
        return execute(jedis -> {
            String value = jedis.hget(ClusterKeys.serverMspt(), serverName);
            return value != null && !value.isEmpty() ? Double.parseDouble(value) : -1;
        }, "Failed to get MSPT for server: " + serverName);
    }

    public long getRoundRobinCounter() {
        return execute(jedis -> (Long) jedis.eval(ROUND_ROBIN_INCR_SCRIPT, List.of(ClusterKeys.roundRobinCounter()), List.of()), "Failed to increment round-robin counter");
    }
}
