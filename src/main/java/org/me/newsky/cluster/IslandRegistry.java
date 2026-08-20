package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks which server each loaded island is hosted on, for cross-server routing.
 */
public class IslandRegistry extends ClusterState {

    public IslandRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    /**
     * Atomically claims the right to host an island, so that two servers can never both load it.
     * The claim is written before the world is loaded and must be released if the load fails.
     *
     * @return true if the claim was taken by this call, false if another server already holds it
     */
    public boolean claimIslandLoadedServer(UUID islandUuid, String serverName) {
        return execute(jedis -> jedis.hsetnx(ClusterKeys.islandServer(), islandUuid.toString(), serverName) == 1L, "Failed to claim island loaded server for: " + islandUuid);
    }

    public void updateIslandLoadedServer(UUID islandUuid, String serverName) {
        run(jedis -> jedis.hset(ClusterKeys.islandServer(), islandUuid.toString(), serverName), "Failed to update island loaded server for: " + islandUuid);
    }

    public void removeIslandLoadedServer(UUID islandUuid) {
        run(jedis -> jedis.hdel(ClusterKeys.islandServer(), islandUuid.toString()), "Failed to remove island loaded server for: " + islandUuid);
    }

    public Optional<String> getIslandLoadedServer(UUID islandUuid) {
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
    public int removeMappingsOfDeadServers() {
        // KEYS[1] = island->server hash, KEYS[2] = the server's heartbeat key
        // ARGV[1] = island uuid,          ARGV[2] = the server the claim pointed at
        String releaseIfDead = "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] and redis.call('exists', KEYS[2]) == 0 then return redis.call('hdel', KEYS[1], ARGV[1]) else return 0 end";

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

                Object removed = jedis.eval(releaseIfDead, List.of(ClusterKeys.islandServer(), ClusterKeys.serverHeartbeat(serverName)), List.of(islandUuid, serverName));
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
     */
    public void removeServerMappings(String serverName) {
        run(jedis -> {
            Map<String, String> mappings = jedis.hgetAll(ClusterKeys.islandServer());
            if (mappings.isEmpty()) {
                return;
            }

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (serverName.equals(entry.getValue())) {
                    jedis.hdel(ClusterKeys.islandServer(), entry.getKey());
                }
            }
        }, "Failed to remove island server mappings for: " + serverName);
    }
}
