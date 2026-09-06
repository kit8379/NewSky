package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks which server each loaded island is hosted on, for cross-server routing.
 */
public class IslandRegistry extends ClusterState {

    /**
     * Deletes atomically, so a mapping another server writes mid-cleanup can never be
     * caught between the snapshot and the delete.
     */
    private static final String REMOVE_SERVER_MAPPINGS_SCRIPT = """
            local entries = redis.call('HGETALL', KEYS[1])
            for i = 1, #entries, 2 do
                if entries[i + 1] == ARGV[1] then
                    redis.call('HDEL', KEYS[1], entries[i])
                end
            end
            return 0
            """;

    public IslandRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
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

    public void removeServerMappings(String serverName) {
        run(jedis -> jedis.eval(REMOVE_SERVER_MAPPINGS_SCRIPT, List.of(ClusterKeys.islandServer()), List.of(serverName)), "Failed to remove island server mappings for: " + serverName);
    }
}
