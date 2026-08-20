package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks which server each loaded island is hosted on, for cross-server routing.
 * <p>
 * Claim lifecycle: a claim is taken atomically ({@code HSETNX}) before the world is loaded,
 * confirmed by the hosting server at the point of load, and only ever released by comparing the
 * stored value first. An unconditional {@code HDEL} is never allowed: between any read and an
 * unconditional delete, another server may have legitimately re-claimed the island, and deleting
 * its fresh claim would let a third server load the same world twice.
 */
public class IslandRegistry extends ClusterState {

    // Public so RedisClaimScriptsTest runs the exact deployed scripts instead of a copy that
    // could silently drift from them.
    // KEYS[1] = island->server hash, ARGV[1] = island uuid, ARGV[2] = expected holder
    public static final String RELEASE_IF_HELD_BY = "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then return redis.call('hdel', KEYS[1], ARGV[1]) else return 0 end";

    // KEYS[1] = island->server hash, ARGV[1] = island uuid, ARGV[2] = claiming server
    public static final String CLAIM_OR_CONFIRM = "local held = redis.call('hget', KEYS[1], ARGV[1]) " + "if held == ARGV[2] then return 1 end " + "if held then return 0 end " + "return redis.call('hsetnx', KEYS[1], ARGV[1], ARGV[2])";

    // KEYS[1] = island->server hash, KEYS[2] = the claim holder's heartbeat key
    // ARGV[1] = island uuid,          ARGV[2] = the server the claim pointed at
    public static final String RELEASE_IF_DEAD = "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] and redis.call('exists', KEYS[2]) == 0 then return redis.call('hdel', KEYS[1], ARGV[1]) else return 0 end";

    public IslandRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    /**
     * Atomically claims the right to host an island, so that two servers can never both load it.
     * The claim is written before the world is loaded and must be released if the load fails.
     *
     * @return true if the claim was taken by this call, false if another server already holds it
     */
    public boolean claimHost(UUID islandUuid, String serverName) {
        return execute(jedis -> jedis.hsetnx(ClusterKeys.islandServer(), islandUuid.toString(), serverName) == 1L, "Failed to claim island loaded server for: " + islandUuid);
    }

    /**
     * Verifies at the point of load that this server is (or atomically becomes) the claim holder.
     * A load must never proceed on a stale request: if the claim meanwhile points at another
     * server, loading here would put the same world on two servers at once.
     *
     * @return true if this server holds the claim after the call, false if another server does
     */
    public boolean claimOrConfirmHost(UUID islandUuid, String serverName) {
        return execute(jedis -> {
            Object result = jedis.eval(CLAIM_OR_CONFIRM, List.of(ClusterKeys.islandServer()), List.of(islandUuid.toString(), serverName));
            return Long.valueOf(1L).equals(result);
        }, "Failed to claim or confirm island loaded server for: " + islandUuid);
    }

    /**
     * Releases a claim, but only if it is still held by the given server. Safe to call on
     * failure paths without knowing whether the claim survived: a claim re-taken by another
     * server is left untouched.
     */
    public void releaseHost(UUID islandUuid, String serverName) {
        run(jedis -> jedis.eval(RELEASE_IF_HELD_BY, List.of(ClusterKeys.islandServer()), List.of(islandUuid.toString(), serverName)), "Failed to release island loaded server for: " + islandUuid);
    }

    public Optional<String> getHost(UUID islandUuid) {
        return execute(jedis -> Optional.ofNullable(jedis.hget(ClusterKeys.islandServer(), islandUuid.toString())), "Failed to get island loaded server for: " + islandUuid);
    }

    /**
     * Reaps island claims held by servers that crashed without cleaning up after themselves.
     * A clean shutdown or restart removes its own mappings; only an unclean death leaves claims
     * behind, and those islands would otherwise route into a dead inbox forever.
     * <p>
     * The liveness check and the removal run atomically inside Redis, so a server that comes
     * back and re-claims an island between our read and our delete cannot lose its fresh claim.
     *
     * @return the number of claims reaped
     */
    public int reapHostsOfDeadServers() {
        return execute(jedis -> {
            Map<String, String> mappings = jedis.hgetAll(ClusterKeys.islandServer());
            if (mappings.isEmpty()) {
                return 0;
            }

            int reaped = 0;
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String islandUuid = entry.getKey();
                String serverName = entry.getValue();

                if (jedis.exists(ClusterKeys.serverHeartbeat(serverName))) {
                    continue;
                }

                Object removed = jedis.eval(RELEASE_IF_DEAD, List.of(ClusterKeys.islandServer(), ClusterKeys.serverHeartbeat(serverName)), List.of(islandUuid, serverName));
                if (Long.valueOf(1L).equals(removed)) {
                    plugin.warning("Reaped island claim " + islandUuid + " held by dead server " + serverName);
                    reaped++;
                }
            }

            return reaped;
        }, "Failed to reap island claims of dead servers");
    }

    /**
     * Removes every island mapping hosted by the given server (used when a server goes away).
     * Each removal re-checks the value inside Redis: during shutdown our heartbeat is already
     * gone, so between the listing and the delete another server may reap and re-claim an
     * island - its fresh claim must survive this sweep.
     */
    public void releaseHostsOf(String serverName) {
        run(jedis -> {
            Map<String, String> mappings = jedis.hgetAll(ClusterKeys.islandServer());
            if (mappings.isEmpty()) {
                return;
            }

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (serverName.equals(entry.getValue())) {
                    jedis.eval(RELEASE_IF_HELD_BY, List.of(ClusterKeys.islandServer()), List.of(entry.getKey(), serverName));
                }
            }
        }, "Failed to remove island server mappings for: " + serverName);
    }
}
