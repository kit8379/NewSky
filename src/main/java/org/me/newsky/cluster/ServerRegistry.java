package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tracks cluster server liveness (heartbeats), selection metrics (MSPT) and the
 * shared round-robin counter. Cleans up island routing when a server goes away.
 */
public class ServerRegistry extends ClusterState {

    private final IslandRegistry islandRegistry;

    public ServerRegistry(NewSky plugin, RedisHandler redisHandler, IslandRegistry islandRegistry) {
        super(plugin, redisHandler);
        this.islandRegistry = islandRegistry;
    }

    public void updateActiveServer(String serverName, boolean lobby, int ttlSeconds) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        run(jedis -> {
            jedis.setex(ClusterKeys.serverHeartbeat(serverName), ttlSeconds, timestamp);

            if (lobby) {
                jedis.del(ClusterKeys.gameServerHeartbeat(serverName));
            } else {
                jedis.setex(ClusterKeys.gameServerHeartbeat(serverName), ttlSeconds, timestamp);
            }
        }, "Failed to update active server for: " + serverName);
    }

    /**
     * Reaps island claims left behind by servers that died without a clean shutdown. Driven by
     * every server's heartbeat tick; the per-claim removal is atomic inside Redis, so concurrent
     * sweeps and a rebooting claim holder are all safe.
     */
    public void reapDeadServerClaims() {
        islandRegistry.removeMappingsOfDeadServers();
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
        return execute(jedis -> {
            long value = jedis.incr(ClusterKeys.roundRobinCounter());

            if (value >= 1_000_000_000L) {
                jedis.set(ClusterKeys.roundRobinCounter(), "0");
                return 0L;
            }

            return value;
        }, "Failed to increment round-robin counter");
    }
}
