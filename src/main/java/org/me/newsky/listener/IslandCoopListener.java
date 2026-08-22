package org.me.newsky.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.island.CoopHandler;

import java.util.UUID;

/**
 * Coop is trust granted to someone currently visiting, so it ends when they leave the cluster.
 * Internal machinery: calls its handler directly rather than going through the public API.
 */
public class IslandCoopListener implements Listener {

    // Allows a proxy switch to re-register the player before cleanup becomes due.
    private static final long CLEANUP_DELAY_MILLIS = 3_000L;
    private static final long CLEANUP_LEASE_MILLIS = 30_000L;
    private static final long DRAIN_INTERVAL_TICKS = 20L;
    private static final int DRAIN_BATCH_SIZE = 100;

    private final NewSky plugin;
    private final CoopHandler coopHandler;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public IslandCoopListener(NewSky plugin, CoopHandler coopHandler,
                              OnlinePlayerRegistry onlinePlayerRegistry) {
        this.plugin = plugin;
        this.coopHandler = coopHandler;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::drainCleanupQueue,
                DRAIN_INTERVAL_TICKS, DRAIN_INTERVAL_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                onlinePlayerRegistry.scheduleCoopCleanup(
                        playerUuid, System.currentTimeMillis() + CLEANUP_DELAY_MILLIS);
                plugin.debug("IslandCoopListener", "Queued durable coop cleanup for player " + playerName);
            } catch (Exception e) {
                plugin.severe("Failed to queue coop cleanup for player " + playerName, e);
            }
        });
    }

    private void drainCleanupQueue() {
        long now = System.currentTimeMillis();
        long leaseUntil = now + CLEANUP_LEASE_MILLIS;
        try {
            for (OnlinePlayerRegistry.CoopCleanupLease lease
                    : onlinePlayerRegistry.claimDueCoopCleanups(now, leaseUntil, DRAIN_BATCH_SIZE)) {
                runCleanup(lease);
            }
        } catch (Exception e) {
            plugin.severe("Failed to drain durable coop cleanup queue", e);
        }
    }

    private void runCleanup(OnlinePlayerRegistry.CoopCleanupLease lease) {
        UUID playerUuid = lease.playerUuid();

        coopHandler.removeAllCoops(playerUuid)
                .thenRun(() -> acknowledgeCleanup(lease))
                .exceptionally(error -> {
                    plugin.severe("Coop cleanup failed for " + playerUuid
                            + "; it will retry after the lease", error);
                    return null;
                });
    }

    private void acknowledgeCleanup(OnlinePlayerRegistry.CoopCleanupLease lease) {
        try {
            if (onlinePlayerRegistry.completeCoopCleanup(lease)) {
                plugin.debug("IslandCoopListener", "Completed durable coop cleanup for "
                        + lease.playerUuid());
            } else {
                plugin.debug("IslandCoopListener", "Ignored stale coop cleanup acknowledgement for "
                        + lease.playerUuid());
            }
        } catch (Exception error) {
            // A missing acknowledgement only causes an idempotent retry.
            plugin.severe("Failed to acknowledge coop cleanup for " + lease.playerUuid(), error);
        }
    }
}
