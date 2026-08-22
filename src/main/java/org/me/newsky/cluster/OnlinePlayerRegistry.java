package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.exceptions.PlayerNotOnlineException;
import org.me.newsky.redis.RedisHandler;

import java.util.HashSet;
import java.util.LinkedHashSet;
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

    private static final long COOP_CLEANUP_GRACE_MILLIS = 3_000L;

    // Public so RedisClaimScriptsTest runs the exact deployed script instead of a copy that
    // could silently drift from it.
    // KEYS[1] = player->server hash, KEYS[2] = player->name hash, KEYS[3] = cleanup queue,
    // KEYS[4] = cleanup lease-token hash
    // ARGV[1] = player uuid, ARGV[2] = encoded owner, ARGV[3] = cleanup due epoch millis
    public static final String REMOVE_IF_ON_SERVER = """
            if redis.call('hget', KEYS[1], ARGV[1]) ~= ARGV[2] then
                return 0
            end

            redis.call('hdel', KEYS[1], ARGV[1])
            redis.call('hdel', KEYS[2], ARGV[1])
            redis.call('zadd', KEYS[3], ARGV[3], ARGV[1])
            redis.call('hdel', KEYS[4], ARGV[1])
            return 1
            """;

    // Same as above plus an incarnation check on KEYS[3] = the server's heartbeat key.
    public static final String REMOVE_IF_ON_DEAD_SERVER = """
            if redis.call('hget', KEYS[1], ARGV[1]) ~= ARGV[2]
                    or redis.call('get', KEYS[3]) == ARGV[3] then
                return 0
            end

            redis.call('hdel', KEYS[1], ARGV[1])
            redis.call('hdel', KEYS[2], ARGV[1])
            redis.call('zadd', KEYS[4], ARGV[4], ARGV[1])
            redis.call('hdel', KEYS[5], ARGV[1])
            return 1
            """;

    // Rolling-upgrade compatibility for entries written by the old server-name-only format.
    public static final String REMOVE_LEGACY_IF_DEAD = """
            if redis.call('hget', KEYS[1], ARGV[1]) ~= ARGV[2]
                    or redis.call('exists', KEYS[3]) ~= 0 then
                return 0
            end

            redis.call('hdel', KEYS[1], ARGV[1])
            redis.call('hdel', KEYS[2], ARGV[1])
            redis.call('zadd', KEYS[4], ARGV[3], ARGV[1])
            redis.call('hdel', KEYS[5], ARGV[1])
            return 1
            """;

    // Registration and cancellation of delayed offline cleanup are one event. If these were
    // separate commands, a cleanup worker could claim the player in the gap and remove coops
    // after the player had already rejoined another server.
    public static final String REGISTER_ONLINE = """
            redis.call('hset', KEYS[1], ARGV[1], ARGV[2])
            redis.call('hset', KEYS[2], ARGV[1], ARGV[3])
            redis.call('zrem', KEYS[3], ARGV[1])
            redis.call('hdel', KEYS[4], ARGV[1])
            return 1
            """;

    // Claims a due cleanup by moving its score forward to a retry lease. A worker crash therefore
    // does not lose the job: another server can claim it when the lease expires. Rejoined players
    // are removed from the queue atomically instead.
    public static final String CLAIM_DUE_COOP_CLEANUP = """
            local score = redis.call('zscore', KEYS[1], ARGV[1])
            if not score or tonumber(score) > tonumber(ARGV[2]) then
                return 0
            end

            if redis.call('hexists', KEYS[2], ARGV[1]) == 1 then
                redis.call('zrem', KEYS[1], ARGV[1])
                redis.call('hdel', KEYS[3], ARGV[1])
                return -1
            end

            redis.call('zadd', KEYS[1], ARGV[3], ARGV[1])
            redis.call('hset', KEYS[3], ARGV[1], ARGV[4])
            return 1
            """;

    // A worker may finish after its lease expired and a newer quit created/re-leased the same
    // player's job. It may acknowledge only its unguessable lease token, never the new job.
    public static final String COMPLETE_COOP_CLEANUP_IF_LEASED = """
            if redis.call('hget', KEYS[2], ARGV[1]) ~= ARGV[2] then
                return 0
            end

            redis.call('zrem', KEYS[1], ARGV[1])
            redis.call('hdel', KEYS[2], ARGV[1])
            return 1
            """;

    public static final String SCHEDULE_COOP_CLEANUP = """
            redis.call('zadd', KEYS[1], ARGV[2], ARGV[1])
            redis.call('hdel', KEYS[2], ARGV[1])
            return 1
            """;

    public record CoopCleanupLease(UUID playerUuid, String token) {
    }

    public OnlinePlayerRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    public void addOnlinePlayer(UUID playerUuid, String playerName, IslandRegistry.HostClaim instance) {
        run(jedis -> jedis.eval(REGISTER_ONLINE,
                List.of(ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers(),
                        ClusterKeys.coopCleanupQueue(), ClusterKeys.coopCleanupLeases()),
                List.of(playerUuid.toString(), instance.encoded(), playerName)),
                "Failed to add online player: " + playerUuid);
    }

    /**
     * Removes the player, but only if this server still owns their entry. If the player already
     * switched to another server (whose join overwrote the entry), this quit is stale and must
     * leave the fresh entry alone.
     */
    public void removeOnlinePlayer(UUID playerUuid, IslandRegistry.HostClaim instance) {
        run(jedis -> jedis.eval(REMOVE_IF_ON_SERVER,
                List.of(ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers(),
                        ClusterKeys.coopCleanupQueue(), ClusterKeys.coopCleanupLeases()),
                List.of(playerUuid.toString(), instance.encoded(),
                        String.valueOf(System.currentTimeMillis() + COOP_CLEANUP_GRACE_MILLIS))),
                "Failed to remove online player: " + playerUuid);
    }

    /**
     * Removes every online entry recorded by the given server (startup and shutdown cleanup).
     * Entries the players have since re-registered on another server survive untouched.
     */
    public void removePlayersOfServer(IslandRegistry.HostClaim instance) {
        run(jedis -> {
            Map<String, String> servers = jedis.hgetAll(ClusterKeys.onlinePlayerServers());
            String cleanupDue = String.valueOf(System.currentTimeMillis() + COOP_CLEANUP_GRACE_MILLIS);

            for (Map.Entry<String, String> entry : servers.entrySet()) {
                if (instance.encoded().equals(entry.getValue())) {
                    jedis.eval(REMOVE_IF_ON_SERVER,
                            List.of(ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers(),
                                    ClusterKeys.coopCleanupQueue(), ClusterKeys.coopCleanupLeases()),
                            List.of(entry.getKey(), instance.encoded(), cleanupDue));
                }
            }
        }, "Failed to remove online players of server instance: " + instance.encoded());
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
            String cleanupDue = String.valueOf(System.currentTimeMillis() + COOP_CLEANUP_GRACE_MILLIS);
            for (Map.Entry<String, String> entry : servers.entrySet()) {
                String encoded = entry.getValue();
                IslandRegistry.HostClaim claim = decodeClaimOrNull(encoded);
                Object removed;

                if (claim != null) {
                    if (claim.instanceId().equals(jedis.get(ClusterKeys.serverHeartbeat(claim.serverName())))) {
                        continue;
                    }
                    removed = jedis.eval(REMOVE_IF_ON_DEAD_SERVER,
                            List.of(ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers(),
                                    ClusterKeys.serverHeartbeat(claim.serverName()),
                                    ClusterKeys.coopCleanupQueue(), ClusterKeys.coopCleanupLeases()),
                            List.of(entry.getKey(), encoded, claim.instanceId(), cleanupDue));
                } else {
                    if (jedis.exists(ClusterKeys.serverHeartbeat(encoded))) {
                        continue;
                    }
                    removed = jedis.eval(REMOVE_LEGACY_IF_DEAD,
                            List.of(ClusterKeys.onlinePlayerServers(), ClusterKeys.onlinePlayers(),
                                    ClusterKeys.serverHeartbeat(encoded), ClusterKeys.coopCleanupQueue(),
                                    ClusterKeys.coopCleanupLeases()),
                            List.of(entry.getKey(), encoded, cleanupDue));
                }

                if (Long.valueOf(1L).equals(removed)) {
                    plugin.warning("Reaped online player " + entry.getKey()
                            + " recorded by inactive server instance " + encoded);
                    reaped++;
                }
            }

            return reaped;
        }, "Failed to reap online players of dead servers");
    }

    private static IslandRegistry.HostClaim decodeClaimOrNull(String encoded) {
        try {
            return IslandRegistry.HostClaim.decode(encoded);
        } catch (IllegalArgumentException | IllegalStateException invalidFormat) {
            return null;
        }
    }

    /**
     * Whether the player is online anywhere in the cluster. Checked by UUID rather than by name so
     * that it cannot disagree with the case-insensitive name lookup in the database, and so that it
     * stays a single field probe instead of pulling every online name across the network.
     */
    public boolean isOnline(UUID playerUuid) {
        return execute(jedis -> jedis.hexists(
                        ClusterKeys.onlinePlayers(), playerUuid.toString()),
                "Failed to check online player: " + playerUuid);
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
        return execute(jedis -> {
            String encoded = jedis.hget(ClusterKeys.onlinePlayerServers(), playerUuid.toString());
            if (encoded == null) {
                return null;
            }
            try {
                return IslandRegistry.HostClaim.decode(encoded).serverName();
            } catch (RuntimeException legacyFormat) {
                return encoded;
            }
        }, "Failed to get online player server: " + playerUuid);
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

    /** Persists delayed cleanup so it survives the server that observed the quit crashing. */
    public void scheduleCoopCleanup(UUID playerUuid, long dueAtMillis) {
        run(jedis -> jedis.eval(SCHEDULE_COOP_CLEANUP,
                        List.of(ClusterKeys.coopCleanupQueue(), ClusterKeys.coopCleanupLeases()),
                        List.of(playerUuid.toString(), String.valueOf(dueAtMillis))),
                "Failed to schedule coop cleanup for: " + playerUuid);
    }

    /**
     * Claims due offline cleanup jobs for a bounded lease. Jobs are acknowledged separately; a
     * process death or failed database operation leaves them retryable after {@code leaseUntil}.
     */
    public Set<CoopCleanupLease> claimDueCoopCleanups(long nowMillis, long leaseUntilMillis, int limit) {
        return execute(jedis -> {
            List<String> due = jedis.zrangeByScore(ClusterKeys.coopCleanupQueue(), 0, nowMillis, 0, limit);
            if (due == null || due.isEmpty()) {
                return Set.of();
            }

            Set<CoopCleanupLease> claimed = new LinkedHashSet<>();
            for (String rawUuid : due) {
                UUID playerUuid;
                try {
                    playerUuid = parseUuid(rawUuid, "coopCleanupQueue");
                } catch (RuntimeException malformed) {
                    jedis.zrem(ClusterKeys.coopCleanupQueue(), rawUuid);
                    plugin.severe("Removed malformed coop cleanup queue entry: " + rawUuid, malformed);
                    continue;
                }
                String token = UUID.randomUUID().toString();
                Object result = jedis.eval(CLAIM_DUE_COOP_CLEANUP,
                        List.of(ClusterKeys.coopCleanupQueue(), ClusterKeys.onlinePlayers(),
                                ClusterKeys.coopCleanupLeases()),
                        List.of(rawUuid, String.valueOf(nowMillis), String.valueOf(leaseUntilMillis), token));
                if (Long.valueOf(1L).equals(result)) {
                    claimed.add(new CoopCleanupLease(playerUuid, token));
                }
            }
            return claimed.isEmpty() ? Set.of() : Set.copyOf(claimed);
        }, "Failed to claim due coop cleanups");
    }

    public boolean completeCoopCleanup(CoopCleanupLease lease) {
        return execute(jedis -> Long.valueOf(1L).equals(jedis.eval(COMPLETE_COOP_CLEANUP_IF_LEASED,
                        List.of(ClusterKeys.coopCleanupQueue(), ClusterKeys.coopCleanupLeases()),
                        List.of(lease.playerUuid().toString(), lease.token()))),
                "Failed to complete leased coop cleanup for: " + lease.playerUuid());
    }
}
