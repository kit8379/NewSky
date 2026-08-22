package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.exceptions.PlayerNotOnlineException;
import org.me.newsky.redis.RedisHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players are online across the whole cluster and on which server.
 */
public class OnlinePlayerRegistry extends ClusterState {

    public OnlinePlayerRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    public void addOnlinePlayer(UUID playerUuid, String playerName, String serverName) {
        run(jedis -> {
            jedis.hset(ClusterKeys.onlinePlayers(), playerUuid.toString(), playerName);
            jedis.hset(ClusterKeys.onlinePlayerServers(), playerUuid.toString(), serverName);
        }, "Failed to add online player: " + playerUuid);
    }

    public void removeOnlinePlayer(UUID playerUuid) {
        run(jedis -> {
            jedis.hdel(ClusterKeys.onlinePlayers(), playerUuid.toString());
            jedis.hdel(ClusterKeys.onlinePlayerServers(), playerUuid.toString());
        }, "Failed to remove online player: " + playerUuid);
    }

    /**
     * Whether the player is online anywhere in the cluster. Checked by UUID rather than by name so
     * that it cannot disagree with the case-insensitive name lookup in the database, and so that it
     * stays a single field probe instead of pulling every online name across the network.
     */
    public boolean isOnline(UUID playerUuid) {
        return execute(jedis -> jedis.hexists(ClusterKeys.onlinePlayers(), playerUuid.toString()), "Failed to check online player: " + playerUuid);
    }

    /**
     * Online state lives in Redis and changes constantly, so this can only ever be a fail-fast
     * filter: the player may quit the moment after it passes.
     */
    public void requireOnline(UUID playerUuid) {
        if (!isOnline(playerUuid)) {
            throw new PlayerNotOnlineException();
        }
    }

    public String getOnlinePlayerServer(UUID playerUuid) {
        return execute(jedis -> jedis.hget(ClusterKeys.onlinePlayerServers(), playerUuid.toString()), "Failed to get online player server: " + playerUuid);
    }

    public Set<UUID> getOnlinePlayerUuids() {
        return execute(jedis -> {
            Set<String> keys = jedis.hkeys(ClusterKeys.onlinePlayers());
            if (keys == null || keys.isEmpty()) {
                return Set.of();
            }

            Set<UUID> result = new HashSet<>(keys.size());
            for (String key : keys) {
                result.add(parseUuid(key, "onlinePlayers"));
            }

            return Set.copyOf(result);
        }, "Failed to get online player UUIDs");
    }

    public Set<String> getOnlinePlayerNames() {
        return execute(jedis -> {
            List<String> values = jedis.hvals(ClusterKeys.onlinePlayers());
            return values == null || values.isEmpty() ? Set.of() : Set.copyOf(values);
        }, "Failed to get online player names");
    }
}
