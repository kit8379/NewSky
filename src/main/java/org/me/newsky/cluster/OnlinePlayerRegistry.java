package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;

import java.util.*;

/**
 * Tracks which players are online across the whole cluster and on which server.
 */
public class OnlinePlayerRegistry extends ClusterState {

    /**
     * Deletes the player's entries only while they are still registered on the given server,
     * so a late quit from the previous server cannot erase the registration written by the
     * server the player switched to.
     */
    private static final String REMOVE_IF_ON_SERVER_SCRIPT = """
            if redis.call('HGET', KEYS[2], ARGV[1]) == ARGV[2] then
                redis.call('HDEL', KEYS[1], ARGV[1])
                redis.call('HDEL', KEYS[2], ARGV[1])
            end
            return 0
            """;

    public OnlinePlayerRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    public void addOnlinePlayer(UUID playerUuid, String playerName, String serverName) {
        run(jedis -> {
            jedis.hset(ClusterKeys.onlinePlayers(), playerUuid.toString(), playerName);
            jedis.hset(ClusterKeys.onlinePlayerServers(), playerUuid.toString(), serverName);
        }, "Failed to add online player: " + playerUuid);
    }

    public void removeOnlinePlayer(UUID playerUuid, String serverName) {
        run(jedis -> jedis.eval(REMOVE_IF_ON_SERVER_SCRIPT, List.of(ClusterKeys.onlinePlayers(), ClusterKeys.onlinePlayerServers()), List.of(playerUuid.toString(), serverName)), "Failed to remove online player: " + playerUuid);
    }

    public void removeAllOnServer(String serverName) {
        run(jedis -> {
            Map<String, String> playerServers = jedis.hgetAll(ClusterKeys.onlinePlayerServers());

            List<String> stale = new ArrayList<>();
            for (Map.Entry<String, String> entry : playerServers.entrySet()) {
                if (serverName.equals(entry.getValue())) {
                    stale.add(entry.getKey());
                }
            }

            if (!stale.isEmpty()) {
                String[] fields = stale.toArray(new String[0]);
                jedis.hdel(ClusterKeys.onlinePlayers(), fields);
                jedis.hdel(ClusterKeys.onlinePlayerServers(), fields);
            }
        }, "Failed to remove online players on server: " + serverName);
    }

    public boolean isOnline(UUID playerUuid) {
        return execute(jedis -> jedis.hexists(ClusterKeys.onlinePlayers(), playerUuid.toString()), "Failed to check online player: " + playerUuid);
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
