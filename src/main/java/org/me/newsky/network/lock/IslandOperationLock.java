package org.me.newsky.network.lock;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cache.RuntimeCache;
import org.me.newsky.exceptions.IslandOperationBusyException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class IslandOperationLock {

    private final NewSky plugin;
    private final RuntimeCache runtimeCache;
    private final String serverID;
    private final long lockTtlMs;
    private final long heartbeatMs;

    public static final long DEFAULT_LOCK_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    public static final long DEFAULT_HEARTBEAT_MS = TimeUnit.MINUTES.toMillis(1);

    public IslandOperationLock(NewSky plugin, RuntimeCache runtimeCache, String serverID) {
        this(plugin, runtimeCache, serverID, DEFAULT_LOCK_TTL_MS, DEFAULT_HEARTBEAT_MS);
    }

    public IslandOperationLock(NewSky plugin, RuntimeCache runtimeCache, String serverID, long lockTtlMs, long heartbeatMs) {
        this.plugin = plugin;
        this.runtimeCache = runtimeCache;
        this.serverID = serverID;
        this.lockTtlMs = lockTtlMs;
        this.heartbeatMs = heartbeatMs;
    }

    public boolean isLocked(UUID islandUuid) {
        return runtimeCache.isIslandOpLocked(islandUuid);
    }

    public <T> CompletableFuture<T> withLock(UUID islandUuid, Supplier<CompletableFuture<T>> action) {
        String token = serverID + ":" + UUID.randomUUID();

        Optional<String> acquired = runtimeCache.tryAcquireIslandOpLock(islandUuid, token, lockTtlMs);
        if (acquired.isEmpty()) {
            long pttl = runtimeCache.getIslandOpLockTtlMillis(islandUuid);
            plugin.debug("IslandOperationLock", "withLock: busy lock for " + islandUuid + " (pttl=" + pttl + "ms)");
            return CompletableFuture.failedFuture(new IslandOperationBusyException());
        }

        long periodTicks = Math.max(1L, heartbeatMs / 50L);

        BukkitTask heartbeat = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            boolean ok = runtimeCache.extendIslandOpLock(islandUuid, token, lockTtlMs);
            if (!ok) {
                plugin.debug("IslandOperationLock", "withLock: failed to extend lock (lost ownership?) island=" + islandUuid);
            }
        }, periodTicks, periodTicks);

        CompletableFuture<T> future;
        try {
            future = action.get();
        } catch (Throwable t) {
            heartbeat.cancel();
            runtimeCache.releaseIslandOpLock(islandUuid, token);
            return CompletableFuture.failedFuture(t);
        }

        return future.whenComplete((res, err) -> {
            heartbeat.cancel();
            runtimeCache.releaseIslandOpLock(islandUuid, token);
        });
    }
}