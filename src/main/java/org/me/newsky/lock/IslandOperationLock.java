// MODIFIED FILE: IslandOperationLock.java
package org.me.newsky.lock;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.state.IslandLockState;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class IslandOperationLock {


    private static final String ISLAND_OP_LOCK_PREFIX = "newsky:lock:island_operation:";

    private static final long LOCK_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long HEARTBEAT_MS = TimeUnit.MINUTES.toMillis(1);

    private final NewSky plugin;
    private final IslandLockState islandLockState;
    private final String serverID;


    public IslandOperationLock(NewSky plugin, IslandLockState islandLockState, String serverID) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.islandLockState = Objects.requireNonNull(islandLockState, "islandLockState");
        this.serverID = Objects.requireNonNull(serverID, "serverID");
    }


    public boolean isLocked(UUID islandUuid) {
        Objects.requireNonNull(islandUuid, "islandUuid");
        return islandLockState.exists(lockKey(islandUuid));
    }

    public <T> CompletableFuture<T> withLock(UUID islandUuid, Supplier<CompletableFuture<T>> action) {
        Objects.requireNonNull(islandUuid, "islandUuid");
        Objects.requireNonNull(action, "action");

        String key = lockKey(islandUuid);
        String token = serverID + ":" + UUID.randomUUID();

        if (!islandLockState.tryAcquire(key, token, LOCK_TTL_MS)) {
            long pttl = islandLockState.pttl(key);
            plugin.debug("IslandOperationLock", "withLock: busy lock for " + islandUuid + " (pttl=" + pttl + "ms)");
            return CompletableFuture.failedFuture(new IslandBusyException());
        }

        long periodTicks = Math.max(1L, HEARTBEAT_MS / 50L);

        BukkitTask heartbeat = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            boolean ok = islandLockState.extend(key, token, LOCK_TTL_MS);
            if (!ok) {
                plugin.debug("IslandOperationLock", "withLock: failed to extend lock (lost ownership?) island=" + islandUuid);
            }
        }, periodTicks, periodTicks);

        CompletableFuture<T> future;
        try {
            future = action.get();
        } catch (Throwable t) {
            heartbeat.cancel();
            islandLockState.release(key, token);
            return CompletableFuture.failedFuture(t);
        }

        return future.whenComplete((result, throwable) -> {
            heartbeat.cancel();
            islandLockState.release(key, token);
        });
    }

    private String lockKey(UUID islandUuid) {
        return ISLAND_OP_LOCK_PREFIX + islandUuid;
    }
}