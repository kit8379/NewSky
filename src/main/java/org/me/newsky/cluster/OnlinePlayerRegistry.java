package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.exceptions.PlayerNotOnlineException;
import org.me.newsky.redis.RedisHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which players are online across the whole cluster and on which server.
 * <p>
 * Removals are always guarded by the server that recorded the entry: a quit processed on one
 * server must never delete the entry a join on another server just wrote (proxy switches deliver
 * the new server's join and the old server's quit with no ordering guarantee between them).
 */
public class OnlinePlayerRegistry extends ClusterState {

    // Public so RedisClaimScriptsTest runs the exact deployed script instead of a copy that
    // could silently drift from it.
    // KEYS[1] = player->server hash, KEYS[2] = player->name hash
    // ARGV[1] = player uuid,          ARGV[2] = the server expected to own the entry
    public static final String REMOVE_IF_ON_SERVER = "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then " + "redis.call('hdel', KEYS[1], ARGV[1]) " + "redis.call('hdel', KEYS[2], ARGV[1]) " + "return 1 else return 0 end";

    // Same as above plus a liveness check on KEYS[3] = the server's heartbeat key.
    public static final String REMOVE_IF_ON_DEAD_SERVER = "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] and redis.call('exists', KEYS[3]) == 0 then " + "redis.call('hdel', KEYS[1], ARGV[1]) " + "redis.call('hdel', KEYS[2], ARGV[1]) " + "return 1 else return 0 end";

    public OnlinePlayerRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    public void addOnlinePlayer(UUID playerUuid, String playerName, String serverName) {
        // Server entry first: a write cut short between the two commands then leaves the player
        // invisible to isOnline (fail closed) instead of online with no owning server, and the
        // dead-server reaper keys off the server hash so the remnant still gets cleaned up.
        run(jedis -> {
            jedis.hset(ClusterKeys.onlinePlayerServers(), playerUuid.toString(), serverName);
            jedis.hset(ClusterKeys.onlinePlayers(), playerUuid.toString(), playerName);
        }, "Failed to add online player: " + playerUuid);
    }

    /**
     * Removes the player, but only if this server still owns their entry. If the player already
     * switched to another server (whose join overwrote the entry), this quit is stale and must
     * leave the fresh entry alone.
     */
    public void removeOnlinePlayer(UUID playerUuid, String serverName) {
        run(jedis -> jedis.eval(REMOVE_IF_ON_SERVER, List.of(ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers()), List.of(playerUuid.toString(), serverName)), "Failed to remove online player: " + playerUuid);
    }

    /**
     * Removes every online entry recorded by the given server (startup and shutdown cleanup).
     * Entries the players have since re-registered on another server survive untouched.
     */
    public void removePlayersOfServer(String serverName) {
        run(jedis -> {
            Map<String, String> servers = jedis.hgetAll(ClusterKeys.onlinePlayerServers());

            for (Map.Entry<String, String> entry : servers.entrySet()) {
                if (serverName.equals(entry.getValue())) {
                    jedis.eval(REMOVE_IF_ON_SERVER, List.of(ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers()), List.of(entry.getKey(), serverName));
                }
            }
        }, "Failed to remove online players of server: " + serverName);
    }

    /**
     * Reaps online entries recorded by servers that crashed without cleanup. Without this, a
     * crashed server's players stay "online" forever: coop cleanup is skipped for them and
     * invites keep routing to phantoms. Check and removal run atomically inside Redis, so a
     * player who meanwhile rejoined elsewhere keeps their fresh entry.
     *
     * @return the number of entries reaped
     */
    public int removePlayersOfDeadServers() {
        return execute(jedis -> {
            Map<String, String> servers = jedis.hgetAll(ClusterKeys.onlinePlayerServers());
            if (servers.isEmpty()) {
                return 0;
            }

            int reaped = 0;
            for (Map.Entry<String, String> entry : servers.entrySet()) {
                String serverName = entry.getValue();

                if (jedis.exists(ClusterKeys.serverHeartbeat(serverName))) {
                    continue;
                }

                Object removed = jedis.eval(REMOVE_IF_ON_DEAD_SERVER, List.of(ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers(), ClusterKeys.serverHeartbeat(serverName)), List.of(entry.getKey(), serverName));
                if (Long.valueOf(1L).equals(removed)) {
                    plugin.warning("Reaped online player " + entry.getKey() + " recorded by dead server " + serverName);
                    reaped++;
                }
            }

            return reaped;
        }, "Failed to reap online players of dead servers");
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
