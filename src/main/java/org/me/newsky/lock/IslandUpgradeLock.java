package org.me.newsky.lock;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.exceptions.IslandBusyException;
import org.me.newsky.state.LockState;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class IslandUpgradeLock {

    private static final String ISLAND_UPGRADE_LOCK_PREFIX = "newsky:lock:island_upgrade:";

    private static final long LOCK_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long HEARTBEAT_MS = TimeUnit.MINUTES.toMillis(1);

    private final NewSky plugin;
    private final LockState lockState;
    private final String serverID;

    public IslandUpgradeLock(NewSky plugin, LockState lockState, String serverID) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lockState = Objects.requireNonNull(lockState, "lockState");
        this.serverID = Objects.requireNonNull(serverID, "serverID");
    }

    public <T> CompletableFuture<T> withLock(UUID islandUuid, Supplier<CompletableFuture<T>> action) {
        Objects.requireNonNull(islandUuid, "islandUuid");
        Objects.requireNonNull(action, "action");

        String key = lockKey(islandUuid);
        String token = serverID + ":" + UUID.randomUUID();

        if (!lockState.tryAcquire(key, token, LOCK_TTL_MS)) {
            long pttl = lockState.pttl(key);
            plugin.debug("IslandUpgradeLock", "withLock: busy lock for " + islandUuid + " (pttl=" + pttl + "ms)");
            return CompletableFuture.failedFuture(new IslandBusyException());
        }

        long periodTicks = Math.max(1L, HEARTBEAT_MS / 50L);

        BukkitTask heartbeat = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            boolean ok = lockState.extend(key, token, LOCK_TTL_MS);
            if (!ok) {
                plugin.debug("IslandUpgradeLock", "withLock: failed to extend lock (lost ownership?) island=" + islandUuid);
            }
        }, periodTicks, periodTicks);

        CompletableFuture<T> future;
        try {
            future = action.get();
        } catch (Throwable t) {
            heartbeat.cancel();
            lockState.release(key, token);
            return CompletableFuture.failedFuture(t);
        }

        return future.whenComplete((result, throwable) -> {
            heartbeat.cancel();
            lockState.release(key, token);
        });
    }

    private String lockKey(UUID islandUuid) {
        return ISLAND_UPGRADE_LOCK_PREFIX + islandUuid;
    }
}