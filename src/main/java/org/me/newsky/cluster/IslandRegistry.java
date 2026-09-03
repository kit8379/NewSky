package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;

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
