// NEW FILE: IslandOpLockManager.java
package org.me.newsky.network.lock;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.redis.RedisCache;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Shared distributed lock wrapper for sensitive island operations (load/unload/delete).
 *
 * <p>Uses Redis lock keys (via RedisCache) and maintains TTL using a heartbeat until the
 * provided action future completes, then releases the lock.
 */
public final class IslandOpLockManager {

    /**
     * Default lock TTL. Must be long enough to cover worst-case island load/unload/delete.
     * Extended periodically by heartbeat while the operation is running.
     */
    public static final long DEFAULT_LOCK_TTL_MS = TimeUnit.MINUTES.toMillis(5);

    /**
     * How often to extend the lock TTL while a sensitive operation is running.
     * Keep it comfortably below TTL to avoid expiry during pauses.
     */
    public static final long DEFAULT_HEARTBEAT_MS = TimeUnit.MINUTES.toMillis(1);

    private final NewSky plugin;
    private final RedisCache redisCache;
    private final String serverID;
    private final long lockTtlMs;
    private final long heartbeatMs;

    public IslandOpLockManager(NewSky plugin, RedisCache redisCache, String serverID) {
        this(plugin, redisCache, serverID, DEFAULT_LOCK_TTL_MS, DEFAULT_HEARTBEAT_MS);
    }

    public IslandOpLockManager(NewSky plugin, RedisCache redisCache, String serverID, long lockTtlMs, long heartbeatMs) {
        this.plugin = plugin;
        this.redisCache = redisCache;
        this.serverID = serverID;
        this.lockTtlMs = lockTtlMs;
        this.heartbeatMs = heartbeatMs;
    }

    public boolean isLocked(UUID islandUuid) {
        return redisCache.isIslandOpLocked(islandUuid);
    }

    /**
     * Run an action under the distributed island op lock.
     *
     * <p>If lock is already held by someone else, completes exceptionally with {@link IslandBusyException}.
     * Lock TTL is extended periodically until the returned future completes, then lock is released.
     */
    public <T> CompletableFuture<T> withLock(UUID islandUuid, Supplier<CompletableFuture<T>> action) {
        String token = serverID + ":" + UUID.randomUUID();

        Optional<String> acquired = redisCache.tryAcquireIslandOpLock(islandUuid, token, lockTtlMs);
        if (acquired.isEmpty()) {
            long pttl = redisCache.getIslandOpLockTtlMillis(islandUuid);
            plugin.debug("IslandOpLockManager", "withLock: busy lock for " + islandUuid + " (pttl=" + pttl + "ms)");
            return CompletableFuture.failedFuture(new IslandBusyException());
        }

        long periodTicks = Math.max(1L, heartbeatMs / 50L);

        BukkitTask heartbeat = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            boolean ok = redisCache.extendIslandOpLock(islandUuid, token, lockTtlMs);
            if (!ok) {
                plugin.debug("IslandOpLockManager", "withLock: failed to extend lock (lost ownership?) island=" + islandUuid);
            }
        }, periodTicks, periodTicks);

        CompletableFuture<T> future;
        try {
            future = action.get();
        } catch (Throwable t) {
            heartbeat.cancel();
            redisCache.releaseIslandOpLock(islandUuid, token);
            return CompletableFuture.failedFuture(t);
        }

        return future.whenComplete((res, err) -> {
            heartbeat.cancel();
            redisCache.releaseIslandOpLock(islandUuid, token);
        });
    }
}