package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tracks cluster server liveness (heartbeats), selection metrics (MSPT) and the
 * shared round-robin counter. Cleans up island routing when a server goes away.
 */
public class ServerRegistry extends ClusterState {

    // One proxy server name may only have one live JVM incarnation. The same script also renews
    // an existing registration owned by this incarnation, so a restarted process cannot take over
    // until the old lease has expired or shut down cleanly.
    public static final String RENEW_INSTANCE = """
            local held = redis.call('get', KEYS[1])
            if held and held ~= ARGV[1] then
                return 0
            end

            if not held then
                redis.call('del', KEYS[3])
            end

            redis.call('setex', KEYS[1], ARGV[2], ARGV[1])
            if ARGV[3] == '1' then
                redis.call('del', KEYS[2])
            else
                redis.call('setex', KEYS[2], ARGV[2], ARGV[1])
            end
            return 1
            """;

    // A late shutdown from an old process must not erase the heartbeat or metrics of a newer
    // process using the same configured server name.
    public static final String REMOVE_INSTANCE_IF_CURRENT = """
            if redis.call('get', KEYS[1]) ~= ARGV[1] then
                return 0
            end

            redis.call('del', KEYS[1])
            redis.call('del', KEYS[2])
            redis.call('hdel', KEYS[3], ARGV[2])
            return 1
            """;

    private final IslandRegistry islandRegistry;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public ServerRegistry(NewSky plugin, RedisHandler redisHandler, IslandRegistry islandRegistry,
                          OnlinePlayerRegistry onlinePlayerRegistry) {
        super(plugin, redisHandler);
        this.islandRegistry = islandRegistry;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    public boolean updateActiveServer(IslandRegistry.HostClaim instance, boolean lobby, int ttlSeconds) {
        return execute(jedis -> {
            Object result = jedis.eval(RENEW_INSTANCE,
                    List.of(ClusterKeys.serverHeartbeat(instance.serverName()),
                            ClusterKeys.gameServerHeartbeat(instance.serverName()),
                            ClusterKeys.messagingInbox(instance.serverName())),
                    List.of(instance.instanceId(), String.valueOf(ttlSeconds), lobby ? "1" : "0"));
            return Long.valueOf(1L).equals(result);
        }, "Failed to update active server for: " + instance.encoded());
    }

    /**
     * Reaps state left behind by servers that died without a clean shutdown: island claims and
     * online player entries. Driven by every server's heartbeat tick; each removal is atomic
     * inside Redis, so concurrent sweeps and a rebooting owner are all safe.
     */
    public void reapDeadServerClaims() {
        islandRegistry.reapHostsOfDeadServers();
        onlinePlayerRegistry.removePlayersOfDeadServers();
    }

    public void removeActiveServer(IslandRegistry.HostClaim instance) {
        run(jedis -> jedis.eval(REMOVE_INSTANCE_IF_CURRENT,
                List.of(ClusterKeys.serverHeartbeat(instance.serverName()),
                        ClusterKeys.gameServerHeartbeat(instance.serverName()), ClusterKeys.serverMspt()),
                List.of(instance.instanceId(), instance.serverName())),
                "Failed to remove active server: " + instance.encoded());

        islandRegistry.releaseHostsOf(instance);
        onlinePlayerRegistry.removePlayersOfServer(instance);
        plugin.debug("ServerRegistry", "Cleaned up state data for server instance: " + instance.encoded());
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
        run(jedis -> jedis.hset(ClusterKeys.serverMspt(), serverName,
                        String.format(Locale.ROOT, "%.2f", mspt)),
                "Failed to update MSPT for server: " + serverName);
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
