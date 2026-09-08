package org.me.newsky.cluster;

import org.me.newsky.NewSky;
import org.me.newsky.redis.RedisHandler;

import java.util.List;
import java.util.UUID;

/**
 * The island claim lock: one hash field per island whose value names the holder.
 * <p>
 * A host value (the bare server id) means the island is loaded there and every operation
 * routes to it. A transient value ({@code server!tx:nonce}) means a server is mutating the
 * unloaded island and others wait their turn in a per-island FIFO queue. Queue entries
 * carry a Redis TIME stamp so a waiter that died is skipped once it is older than the wait
 * it would have given up after. Every write goes through a script; nothing else touches
 * the hash, and no peer ever releases another server's claim (no failover).
 */
public class IslandRegistry extends ClusterState {

    // Strictly shorter than the cross-server request timeout (30s), so a remote load that
    // waits on a claim fails typed at the requester instead of timing out untyped there.
    public static final long CLAIM_WAIT_MILLIS = 20_000L;

    private static final String TRANSIENT_MARKER = "!tx:";
    // A waiter gives up CLAIM_WAIT_MILLIS after enqueueing; anything older is dead.
    private static final long QUEUE_STALE_MILLIS = CLAIM_WAIT_MILLIS + 5_000L;
    private static final int QUEUE_TTL_SECONDS = 60;

    /**
     * nextLive drops dead waiters from the queue head and returns the first live waiter's
     * value (nil when empty) plus the current Redis time in millis. dropOwn removes every
     * entry the given value has in the queue.
     */
    private static final String HELPERS = """
            local function nextLive(queue, stale)
                local time = redis.call('TIME')
                local now = time[1] * 1000 + math.floor(time[2] / 1000)
                local head = redis.call('LINDEX', queue, 0)
                while head do
                    local sep = string.find(head, '|', 1, true)
                    if now - tonumber(string.sub(head, sep + 1)) > stale then
                        redis.call('LPOP', queue)
                        head = redis.call('LINDEX', queue, 0)
                    else
                        return string.sub(head, 1, sep - 1), now
                    end
                end
                return nil, now
            end
            local function dropOwn(queue, value)
                local mine = value .. '|'
                for _, entry in ipairs(redis.call('LRANGE', queue, 0, -1)) do
                    if string.sub(entry, 1, #mine) == mine then
                        redis.call('LREM', queue, 1, entry)
                    end
                end
            end
            """;

    /**
     * A host holder is reported (with its liveness) so the caller routes there instead of
     * waiting; a transient holder whose server has no heartbeat is DEAD (nobody takes its
     * claim, but nobody waits on it either). Otherwise the lock is taken when it is free and
     * nobody live is ahead in the queue; a transient holder or an earlier waiter means
     * queueing (or BUSY when the caller asked not to queue). Every reply that leaves the lock
     * free, or hands it to a host, names the next live waiter so the caller wakes it: a
     * waiter behind a new host must learn to route there, and a lost wake-up would otherwise
     * cost it the full wait.
     * KEYS: island hash, claim queue. ARGV: island, value, stale millis, mode, queue ttl,
     * heartbeat key prefix.
     */
    private static final String ACQUIRE_SCRIPT = HELPERS + """
            local stale = tonumber(ARGV[3])
            local holder = redis.call('HGET', KEYS[1], ARGV[1])
            if holder then
                local marker = string.find(holder, '!tx:', 1, true)
                if not marker then
                    dropOwn(KEYS[2], ARGV[2])
                    local headValue = nextLive(KEYS[2], stale)
                    return {'HOST', holder, redis.call('EXISTS', ARGV[6] .. holder), headValue}
                end
                if redis.call('EXISTS', ARGV[6] .. string.sub(holder, 1, marker - 1)) == 0 then
                    dropOwn(KEYS[2], ARGV[2])
                    return {'DEAD'}
                end
            end
            local headValue, now = nextLive(KEYS[2], stale)
            if not holder and (not headValue or headValue == ARGV[2]) then
                if headValue then
                    redis.call('LPOP', KEYS[2])
                end
                redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
                if string.find(ARGV[2], '!tx:', 1, true) then
                    return {'ACQUIRED'}
                end
                return {'ACQUIRED', (nextLive(KEYS[2], stale))}
            end
            local status = 'BUSY'
            if ARGV[4] == 'queue' then
                redis.call('RPUSH', KEYS[2], ARGV[2] .. '|' .. now)
                redis.call('EXPIRE', KEYS[2], tonumber(ARGV[5]))
                status = 'QUEUED'
            end
            if not holder then
                return {status, headValue}
            end
            return {status}
            """;

    /**
     * Compare-and-delete, then report the first live waiter so it can be woken. The waiter
     * claims for itself: the lock is never written on another server's behalf, so a waiter
     * that died in between simply never takes it.
     * KEYS: island hash, claim queue. ARGV: island, value, stale millis.
     */
    private static final String RELEASE_SCRIPT = HELPERS + """
            if redis.call('HGET', KEYS[1], ARGV[1]) ~= ARGV[2] then
                return {'NOTMINE'}
            end
            redis.call('HDEL', KEYS[1], ARGV[1])
            return {'RELEASED', (nextLive(KEYS[2], tonumber(ARGV[3])))}
            """;

    /**
     * A waiter giving up leaves the queue, and when the lock is free hands its turn to the
     * next live waiter. KEYS: island hash, claim queue. ARGV: island, value, stale millis.
     */
    private static final String ABANDON_SCRIPT = HELPERS + """
            dropOwn(KEYS[2], ARGV[2])
            if redis.call('HGET', KEYS[1], ARGV[1]) then
                return {'GONE'}
            end
            return {'GONE', (nextLive(KEYS[2], tonumber(ARGV[3])))}
            """;

    /**
     * Reads the host and its heartbeat in one round trip; a transient holder reads as no
     * host, since routing only ever follows hosts. ARGV: island, heartbeat key prefix.
     */
    private static final String RESOLVE_HOST_SCRIPT = """
            local holder = redis.call('HGET', KEYS[1], ARGV[1])
            if not holder or string.find(holder, '!tx:', 1, true) then
                return {'NONE'}
            end
            return {'HOST', holder, redis.call('EXISTS', ARGV[2] .. holder)}
            """;

    /**
     * Same-name startup and shutdown cleanup: drops this server's host and transient claims
     * atomically, so a claim another server writes mid-cleanup can never be caught between
     * the snapshot and the delete. ARGV: server name, transient value prefix.
     */
    private static final String REMOVE_SERVER_MAPPINGS_SCRIPT = """
            local entries = redis.call('HGETALL', KEYS[1])
            for i = 1, #entries, 2 do
                local value = entries[i + 1]
                if value == ARGV[1] or string.sub(value, 1, #ARGV[2]) == ARGV[2] then
                    redis.call('HDEL', KEYS[1], entries[i])
                end
            end
            return 0
            """;

    public enum Status {
        ACQUIRED, HOST, DEAD, QUEUED, BUSY
    }

    /**
     * @param holder    the host server when status is HOST
     * @param hostAlive whether that host's heartbeat is present
     * @param wake      the next live waiter to wake, or null
     */
    public record Claim(Status status, String holder, boolean hostAlive, String wake) {
    }

    public record Host(String server, boolean alive) {
    }

    public IslandRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    public static String transientValue(String serverName) {
        return serverName + TRANSIENT_MARKER + UUID.randomUUID();
    }

    public static String serverOf(String claimValue) {
        int marker = claimValue.indexOf(TRANSIENT_MARKER);
        return marker < 0 ? claimValue : claimValue.substring(0, marker);
    }

    public Claim acquire(UUID islandUuid, String value, boolean queue) {
        List<String> reply = execute(jedis -> reply(jedis.eval(ACQUIRE_SCRIPT, keys(islandUuid), List.of(islandUuid.toString(), value, String.valueOf(QUEUE_STALE_MILLIS), queue ? "queue" : "noqueue", String.valueOf(QUEUE_TTL_SECONDS), ClusterKeys.serverHeartbeatPrefix()))), "Failed to acquire island claim for: " + islandUuid);

        Status status = Status.valueOf(reply.getFirst());
        return switch (status) {
            case ACQUIRED, QUEUED, BUSY -> new Claim(status, null, false, reply.size() > 1 ? reply.get(1) : null);
            case HOST -> new Claim(status, reply.get(1), "1".equals(reply.get(2)), reply.size() > 3 ? reply.get(3) : null);
            case DEAD -> new Claim(status, null, false, null);
        };
    }

    /**
     * Returns the waiter now first in line, or null when nobody is waiting.
     */
    public String release(UUID islandUuid, String value) {
        List<String> reply = execute(jedis -> reply(jedis.eval(RELEASE_SCRIPT, keys(islandUuid), List.of(islandUuid.toString(), value, String.valueOf(QUEUE_STALE_MILLIS)))), "Failed to release island claim for: " + islandUuid);

        if ("NOTMINE".equals(reply.getFirst())) {
            plugin.warning("Island claim " + islandUuid + " was not held by " + value + " at release.");
            return null;
        }

        return reply.size() > 1 ? reply.get(1) : null;
    }

    /**
     * Leaves the queue without acquiring. Returns the waiter to wake when the lock is free
     * and someone live is now first in line, or null.
     */
    public String abandon(UUID islandUuid, String value) {
        List<String> reply = execute(jedis -> reply(jedis.eval(ABANDON_SCRIPT, keys(islandUuid), List.of(islandUuid.toString(), value, String.valueOf(QUEUE_STALE_MILLIS)))), "Failed to abandon island claim wait for: " + islandUuid);
        return reply.size() > 1 ? reply.get(1) : null;
    }

    /**
     * Returns the hosting server and whether it is alive, or null when the island is not
     * hosted anywhere (absent or only transiently claimed).
     */
    public Host resolveHost(UUID islandUuid) {
        List<String> reply = execute(jedis -> reply(jedis.eval(RESOLVE_HOST_SCRIPT, List.of(ClusterKeys.islandServer()), List.of(islandUuid.toString(), ClusterKeys.serverHeartbeatPrefix()))), "Failed to resolve island host for: " + islandUuid);

        if ("NONE".equals(reply.getFirst())) {
            return null;
        }

        return new Host(reply.get(1), "1".equals(reply.get(2)));
    }

    public void removeServerMappings(String serverName) {
        run(jedis -> jedis.eval(REMOVE_SERVER_MAPPINGS_SCRIPT, List.of(ClusterKeys.islandServer()), List.of(serverName, serverName + TRANSIENT_MARKER)), "Failed to remove island server mappings for: " + serverName);
    }

    private static List<String> keys(UUID islandUuid) {
        return List.of(ClusterKeys.islandServer(), ClusterKeys.islandClaimQueue(islandUuid));
    }

    private static List<String> reply(Object raw) {
        return ((List<?>) raw).stream().map(String::valueOf).toList();
    }
}
