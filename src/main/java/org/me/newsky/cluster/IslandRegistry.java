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
    // KEYS[1] = island->server hash, ARGV[1] = island uuid, ARGV[2] = encoded expected holder
    public static final String RELEASE_IF_HELD_BY = "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then return redis.call('hdel', KEYS[1], ARGV[1]) else return 0 end";

    // Every acquisition also proves that this exact JVM incarnation still owns its heartbeat.
    // This closes the gap between detecting lease loss and the Bukkit main thread disabling the
    // plugin: an already-fenced process cannot re-acquire a claim while shutdown is pending.
    // KEYS[1] = island->server hash, KEYS[2] = claimant heartbeat
    // ARGV[1] = island uuid, ARGV[2] = encoded claimant, ARGV[3] = claimant instance id
    public static final String CLAIM_IF_LIVE = "if redis.call('get', KEYS[2]) ~= ARGV[3] then return -1 end "
            + "return redis.call('hsetnx', KEYS[1], ARGV[1], ARGV[2])";

    public static final String CLAIM_OR_CONFIRM = "if redis.call('get', KEYS[2]) ~= ARGV[3] then return -1 end "
            + "local held = redis.call('hget', KEYS[1], ARGV[1]) "
            + "if held == ARGV[2] then return 1 end "
            + "if held then return 0 end "
            + "return redis.call('hsetnx', KEYS[1], ARGV[1], ARGV[2])";

    // KEYS[1] = island->server hash, KEYS[2] = the claim holder's heartbeat key
    // ARGV[1] = island uuid, ARGV[2] = encoded holder, ARGV[3] = expected instance id
    // A missing heartbeat and a heartbeat from a newer incarnation both fence the old claim.
    public static final String RELEASE_IF_DEAD = "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] and redis.call('get', KEYS[2]) ~= ARGV[3] then return redis.call('hdel', KEYS[1], ARGV[1]) else return 0 end";

    // Rolling-upgrade compatibility for claims written by the old server-name-only protocol.
    public static final String RELEASE_LEGACY_IF_DEAD = "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] and redis.call('exists', KEYS[2]) == 0 then return redis.call('hdel', KEYS[1], ARGV[1]) else return 0 end";

    // KEYS[1] = island->server hash, KEYS[2] = claimant heartbeat
    public static final String ACQUIRE_WRITE_AUTHORITY = "if redis.call('get', KEYS[2]) ~= ARGV[3] then return 'fenced' end "
            + "local held = redis.call('hget', KEYS[1], ARGV[1]) "
            + "if held == ARGV[2] then return 'host' end "
            + "if held then return 'other' end "
            + "redis.call('hsetnx', KEYS[1], ARGV[1], ARGV[2]) "
            + "return 'claimed'";

    public static final String IS_LIVE_HOLDER = "if redis.call('get', KEYS[2]) == ARGV[3] and "
            + "redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then return 1 else return 0 end";

    /** Outcome of {@link #acquireWriteAuthority}: who may execute a write for this island. */
    public enum WriteAuthority {
        /** This server hosts the island; write and apply the delta, keep the claim. */
        HOST,
        /** The island was unclaimed; this server now holds a temporary write claim it must release. */
        CLAIMED,
        /** Another server holds the claim; the write must be routed there instead. */
        OTHER,
        /** This JVM no longer owns the configured server name's heartbeat and must not write. */
        FENCED
    }

    /**
     * A claim identifies one JVM incarnation, not merely a proxy server name. Reusing a server
     * name after restart therefore cannot confirm or release work owned by the previous process.
     */
    public record HostClaim(String serverName, String instanceId) {
        private static final int UUID_TEXT_LENGTH = 36;

        public HostClaim {
            if (serverName == null || serverName.isBlank()) {
                throw new IllegalArgumentException("Server name cannot be blank");
            }
            UUID.fromString(instanceId);
        }

        public String encoded() {
            return instanceId + ":" + serverName;
        }

        public static HostClaim decode(String encoded) {
            if (encoded == null || encoded.length() <= UUID_TEXT_LENGTH || encoded.charAt(UUID_TEXT_LENGTH) != ':') {
                throw new IllegalStateException("Invalid island host claim: " + encoded);
            }
            return new HostClaim(encoded.substring(UUID_TEXT_LENGTH + 1), encoded.substring(0, UUID_TEXT_LENGTH));
        }
    }

    public IslandRegistry(NewSky plugin, RedisHandler redisHandler) {
        super(plugin, redisHandler);
    }

    /**
     * Atomically claims the right to host an island, so that two servers can never both load it.
     * The claim is written before the world is loaded and must be released if the load fails.
     *
     * @return true if the claim was taken by this call, false if another server already holds it
     */
    public boolean claimHost(UUID islandUuid, HostClaim claimant) {
        return execute(jedis -> {
            Object result = jedis.eval(CLAIM_IF_LIVE,
                    List.of(ClusterKeys.islandServer(), ClusterKeys.serverHeartbeat(claimant.serverName())),
                    List.of(islandUuid.toString(), claimant.encoded(), claimant.instanceId()));
            return Long.valueOf(1L).equals(result);
        }, "Failed to claim island loaded server for: " + islandUuid);
    }

    /**
     * Verifies at the point of load that this server is (or atomically becomes) the claim holder.
     * A load must never proceed on a stale request: if the claim meanwhile points at another
     * server, loading here would put the same world on two servers at once.
     *
     * @return true if this server holds the claim after the call, false if another server does
     */
    public boolean claimOrConfirmHost(UUID islandUuid, HostClaim claimant) {
        return execute(jedis -> {
            Object result = jedis.eval(CLAIM_OR_CONFIRM,
                    List.of(ClusterKeys.islandServer(), ClusterKeys.serverHeartbeat(claimant.serverName())),
                    List.of(islandUuid.toString(), claimant.encoded(), claimant.instanceId()));
            return Long.valueOf(1L).equals(result);
        }, "Failed to claim or confirm island loaded server for: " + islandUuid);
    }

    /**
     * Decides atomically who may execute a write for this island: the claim holder itself (HOST),
     * this server under a temporary claim when nobody held one (CLAIMED - the caller must release
     * it), or nobody here (OTHER - route to the holder). Writes must never run without authority:
     * a write executed beside someone else's claim would commit a change the real host's
     * in-memory copy never hears about.
     */
    public WriteAuthority acquireWriteAuthority(UUID islandUuid, HostClaim claimant) {
        return execute(jedis -> {
            Object result = jedis.eval(ACQUIRE_WRITE_AUTHORITY,
                    List.of(ClusterKeys.islandServer(), ClusterKeys.serverHeartbeat(claimant.serverName())),
                    List.of(islandUuid.toString(), claimant.encoded(), claimant.instanceId()));
            return switch (String.valueOf(result)) {
                case "host" -> WriteAuthority.HOST;
                case "claimed" -> WriteAuthority.CLAIMED;
                case "fenced" -> WriteAuthority.FENCED;
                default -> WriteAuthority.OTHER;
            };
        }, "Failed to acquire write authority for island: " + islandUuid);
    }

    public boolean holdsLiveClaim(UUID islandUuid, HostClaim claimant) {
        return execute(jedis -> {
            Object result = jedis.eval(IS_LIVE_HOLDER,
                    List.of(ClusterKeys.islandServer(), ClusterKeys.serverHeartbeat(claimant.serverName())),
                    List.of(islandUuid.toString(), claimant.encoded(), claimant.instanceId()));
            return Long.valueOf(1L).equals(result);
        }, "Failed to verify live island claim for: " + islandUuid);
    }

    /**
     * Releases a claim, but only if it is still held by the given server. Safe to call on
     * failure paths without knowing whether the claim survived: a claim re-taken by another
     * server is left untouched.
     */
    public void releaseHost(UUID islandUuid, HostClaim claimant) {
        run(jedis -> jedis.eval(RELEASE_IF_HELD_BY, List.of(ClusterKeys.islandServer()), List.of(islandUuid.toString(), claimant.encoded())), "Failed to release island loaded server for: " + islandUuid);
    }

    public Optional<String> getHost(UUID islandUuid) {
        return execute(jedis -> {
            String encoded = jedis.hget(ClusterKeys.islandServer(), islandUuid.toString());
            if (encoded == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(HostClaim.decode(encoded).serverName());
            } catch (RuntimeException legacyFormat) {
                return Optional.of(encoded);
            }
        }, "Failed to get island loaded server for: " + islandUuid);
    }

    public Optional<HostClaim> getHostClaim(UUID islandUuid) {
        return execute(jedis -> {
            String encoded = jedis.hget(ClusterKeys.islandServer(), islandUuid.toString());
            if (encoded == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(HostClaim.decode(encoded));
            } catch (RuntimeException legacyFormat) {
                // A rolling upgrade may still expose server-name-only claims. They remain valid
                // routing hints via getHost(), but do not pretend to identify a JVM incarnation.
                return Optional.empty();
            }
        }, "Failed to get island loaded server for: " + islandUuid);
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
                String encoded = entry.getValue();
                HostClaim claim;
                try {
                    claim = HostClaim.decode(encoded);
                } catch (RuntimeException malformed) {
                    // Pre-incarnation versions stored just the proxy server name. Preserve those
                    // claims while that legacy heartbeat exists; otherwise rolling deployment of
                    // one new server could split-brain every island still hosted by old servers.
                    if (jedis.exists(ClusterKeys.serverHeartbeat(encoded))) {
                        continue;
                    }
                    Object removed = jedis.eval(RELEASE_LEGACY_IF_DEAD,
                            List.of(ClusterKeys.islandServer(), ClusterKeys.serverHeartbeat(encoded)),
                            List.of(islandUuid, encoded));
                    if (Long.valueOf(1L).equals(removed)) {
                        plugin.warning("Reaped legacy island claim " + islandUuid + " held by inactive server " + encoded);
                        reaped++;
                    }
                    continue;
                }

                String liveInstance = jedis.get(ClusterKeys.serverHeartbeat(claim.serverName()));
                if (claim.instanceId().equals(liveInstance)) {
                    continue;
                }

                Object removed = jedis.eval(RELEASE_IF_DEAD, List.of(ClusterKeys.islandServer(), ClusterKeys.serverHeartbeat(claim.serverName())), List.of(islandUuid, claim.encoded(), claim.instanceId()));
                if (Long.valueOf(1L).equals(removed)) {
                    plugin.warning("Reaped island claim " + islandUuid + " held by inactive instance " + claim.encoded());
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
    public void releaseHostsOf(HostClaim claimant) {
        run(jedis -> {
            Map<String, String> mappings = jedis.hgetAll(ClusterKeys.islandServer());
            if (mappings.isEmpty()) {
                return;
            }

            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                if (claimant.encoded().equals(entry.getValue())) {
                    jedis.eval(RELEASE_IF_HELD_BY, List.of(ClusterKeys.islandServer()), List.of(entry.getKey(), claimant.encoded()));
                }
            }
        }, "Failed to remove island server mappings for: " + claimant.encoded());
    }
}
