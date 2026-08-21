package org.me.newsky.scheduler;

import org.bukkit.scheduler.BukkitTask;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.cluster.ServerRegistry;
import org.me.newsky.config.ConfigHandler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HeartbeatScheduler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final ServerRegistry serverRegistry;
    private final IslandRegistry.HostClaim instance;
    private final int heartbeatInterval;
    private final int heartbeatTtlSeconds;
    private final long leaseSafetyNanos;
    private final Runnable leaseLostAction;
    private final AtomicBoolean fenced = new AtomicBoolean();

    private BukkitTask heartbeatTask;
    private volatile long lastSuccessfulHeartbeatNanos;

    public HeartbeatScheduler(NewSky plugin, ConfigHandler config, ServerRegistry serverRegistry, IslandRegistry.HostClaim instance, Runnable leaseLostAction) {
        this.plugin = plugin;
        this.config = config;
        this.serverRegistry = serverRegistry;
        this.instance = instance;
        this.leaseLostAction = leaseLostAction;
        this.heartbeatInterval = config.getHeartbeatInterval();
        this.heartbeatTtlSeconds = Math.max(heartbeatInterval * 3, heartbeatInterval + 5);
        this.leaseSafetyNanos = TimeUnit.SECONDS.toNanos(heartbeatTtlSeconds - heartbeatInterval);
    }

    public void start() {
        if (heartbeatTask != null) {
            plugin.debug("HeartbeatScheduler", "Heartbeat task is already running. No action taken.");
            return;
        }

        // Register synchronously before opening messenger intake. A duplicate live server name is
        // refused rather than deleting the other process's heartbeat and claims.
        if (!serverRegistry.updateActiveServer(instance, config.isLobbyOnly(), heartbeatTtlSeconds)) {
            throw new IllegalStateException("Another live NewSky instance already owns server name: " + instance.serverName());
        }
        lastSuccessfulHeartbeatNanos = System.nanoTime();
        serverRegistry.reapDeadServerClaims();

        plugin.debug("HeartbeatScheduler", "Starting heartbeat task with interval: " + heartbeatInterval + " seconds, ttl: " + heartbeatTtlSeconds + " seconds.");
        heartbeatTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::heartbeatTick, heartbeatInterval * 20L, heartbeatInterval * 20L);
        plugin.debug("HeartbeatScheduler", "Heartbeat task started successfully.");
    }

    public void stop() {
        stop(true);
    }

    /**
     * Stops renewal. With cleanup disabled, exact claims remain fenced by the heartbeat TTL; this
     * is the safe shutdown path when an admitted operation may still be finishing.
     */
    public void stop(boolean cleanupImmediately) {
        if (heartbeatTask != null) {
            plugin.debug("HeartbeatScheduler", "Stopping heartbeat task for server instance: " + instance.encoded());
            heartbeatTask.cancel();
            heartbeatTask = null;
            plugin.debug("HeartbeatScheduler", "Heartbeat task stopped.");
        }

        if (!cleanupImmediately) {
            plugin.warning("Leaving heartbeat and claims to expire for in-flight operation safety: " + instance.encoded());
            return;
        }

        plugin.debug("HeartbeatScheduler", "Performing shutdown cleanup for server instance: " + instance.encoded());
        try {
            serverRegistry.removeActiveServer(instance);
        } catch (RuntimeException error) {
            // Shutdown must continue through world/database cleanup even when Redis is unavailable.
            plugin.severe("Failed to remove heartbeat during shutdown for " + instance.encoded(), error);
        }
        plugin.debug("HeartbeatScheduler", "Shutdown cleanup complete.");
    }

    private void heartbeatTick() {
        if (fenced.get()) {
            return;
        }

        long now = System.nanoTime();
        if (now - lastSuccessfulHeartbeatNanos >= leaseSafetyNanos) {
            fence("Heartbeat renewal resumed after the lease safety window");
            return;
        }

        try {
            if (!serverRegistry.updateActiveServer(instance, config.isLobbyOnly(), heartbeatTtlSeconds)) {
                fence("Server name is now owned by another live instance");
                return;
            }

            lastSuccessfulHeartbeatNanos = System.nanoTime();
            serverRegistry.reapDeadServerClaims();
            plugin.debug("HeartbeatScheduler", "Renewed heartbeat for instance: " + instance.encoded());
        } catch (RuntimeException error) {
            long age = System.nanoTime() - lastSuccessfulHeartbeatNanos;
            if (age >= leaseSafetyNanos) {
                fence("Redis heartbeat could not be renewed before the lease safety deadline");
            }
        }
    }

    private void fence(String reason) {
        if (!fenced.compareAndSet(false, true)) {
            return;
        }

        plugin.severe("Cluster lease lost for " + instance.encoded() + ": " + reason + ". Self-fencing this server before another host can take over.");
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        leaseLostAction.run();
    }
}
