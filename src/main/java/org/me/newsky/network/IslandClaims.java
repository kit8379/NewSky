package org.me.newsky.network;

import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.IslandRegistry;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.messaging.CrossServerMessenger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Acquires and releases island claims for this server, waiting in the island's FIFO queue
 * when a transient holder is ahead. A releasing server wakes the first waiter through the
 * messenger; the waiter then claims for itself.
 */
public final class IslandClaims {

    public static final String ACTION_CLAIM_GRANTED = "island.claim.granted";

    private final NewSky plugin;
    private final IslandRegistry islandRegistry;
    private final CrossServerMessenger messenger;
    private final String serverID;
    private final Map<String, CompletableFuture<Void>> waiters = new ConcurrentHashMap<>();

    public IslandClaims(NewSky plugin, IslandRegistry islandRegistry, CrossServerMessenger messenger, String serverID) {
        this.plugin = plugin;
        this.islandRegistry = islandRegistry;
        this.messenger = messenger;
        this.serverID = serverID;
    }

    public String hostValue() {
        return serverID;
    }

    public String transientValue() {
        return IslandRegistry.transientValue(serverID);
    }

    /**
     * Completes with null once this server holds the claim, or with the host's name when
     * another server hosts the island so the caller routes there instead. A lost wake-up
     * costs only the wait timeout, after which one last attempt runs before failing.
     */
    public CompletableFuture<String> acquire(UUID islandUuid, String value) {
        return attempt(islandUuid, value, true, System.currentTimeMillis() + IslandRegistry.CLAIM_WAIT_MILLIS);
    }

    private CompletableFuture<String> attempt(UUID islandUuid, String value, boolean queue, long deadline) {
        String key = waiterKey(islandUuid, value);
        CompletableFuture<Void> wake = new CompletableFuture<>();
        // Registered before the script runs: a release racing our enqueue may wake us first.
        waiters.put(key, wake);

        IslandRegistry.Claim claim;
        try {
            claim = islandRegistry.acquire(islandUuid, value, queue);
        } catch (RuntimeException error) {
            waiters.remove(key, wake);
            return CompletableFuture.failedFuture(error);
        }

        if (claim.wake() != null) {
            wake(islandUuid, claim.wake());
        }

        if (claim.status() == IslandRegistry.Status.ACQUIRED) {
            waiters.remove(key, wake);
            return CompletableFuture.completedFuture(null);
        }

        // No failover: a silent holder keeps its claim until it restarts under the same name.
        if (claim.status() == IslandRegistry.Status.HOST) {
            waiters.remove(key, wake);
            return claim.hostAlive() ? CompletableFuture.completedFuture(claim.holder()) : CompletableFuture.failedFuture(new NoActiveServerException());
        }

        if (claim.status() == IslandRegistry.Status.DEAD) {
            waiters.remove(key, wake);
            return CompletableFuture.failedFuture(new NoActiveServerException());
        }

        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            waiters.remove(key, wake);
            String next = islandRegistry.abandon(islandUuid, value);
            if (next != null) {
                wake(islandUuid, next);
            }
            return CompletableFuture.failedFuture(new IllegalStateException("Timed out waiting for the claim on island " + islandUuid));
        }

        return wake.orTimeout(remaining, TimeUnit.MILLISECONDS).handle((ignored, error) -> null).thenComposeAsync(ignored -> {
            waiters.remove(key, wake);
            return attempt(islandUuid, value, false, deadline);
        }, plugin.getBukkitAsyncExecutor());
    }

    public void release(UUID islandUuid, String value) {
        String next = islandRegistry.release(islandUuid, value);
        if (next != null) {
            wake(islandUuid, next);
        }
    }

    public void grant(UUID islandUuid, String value) {
        CompletableFuture<Void> wake = waiters.get(waiterKey(islandUuid, value));
        if (wake != null) {
            wake.complete(null);
        }
    }

    private void wake(UUID islandUuid, String waiterValue) {
        String server = IslandRegistry.serverOf(waiterValue);
        if (server.equals(serverID)) {
            grant(islandUuid, waiterValue);
            return;
        }

        JSONObject payload = new JSONObject();
        payload.put("islandUuid", islandUuid.toString());
        payload.put("value", waiterValue);
        // Fire-and-forget: the waiter falls back to a timed retry if this never lands.
        messenger.requestVoid(server, ACTION_CLAIM_GRANTED, payload).exceptionally(error -> {
            plugin.debug("IslandClaims", "Could not wake waiter " + waiterValue + " for island " + islandUuid + ": " + error);
            return null;
        });
    }

    private static String waiterKey(UUID islandUuid, String value) {
        return islandUuid + "|" + value;
    }
}
