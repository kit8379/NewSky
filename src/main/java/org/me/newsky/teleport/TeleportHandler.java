package org.me.newsky.teleport;

import org.bukkit.Location;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportHandler {

    // A pending teleport is only meaningful for the few seconds of a proxy hop. After that the
    // world it points into may be unloaded, and springing a stale teleport on a player who joins
    // much later would move them somewhere they never asked to go now.
    private static final long PENDING_TELEPORT_TTL_MILLIS = 60_000L;

    private final ConcurrentHashMap<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    public void addPendingTeleport(UUID playerUuid, Location location) {
        long now = System.currentTimeMillis();
        pendingTeleports.values().removeIf(pending -> now - pending.createdAt() > PENDING_TELEPORT_TTL_MILLIS);
        pendingTeleports.put(playerUuid, new PendingTeleport(UUID.randomUUID(), location, now));
    }

    public void removePendingTeleport(UUID playerUuid) {
        pendingTeleports.remove(playerUuid);
    }

    public Location getPendingTeleport(UUID playerUuid) {
        PendingTeleport pending = getLivePending(playerUuid);
        return pending == null ? null : pending.location();
    }

    /** Returns a stable token so completion cannot erase a newer request for the same player. */
    public PendingTeleportTicket claimPendingTeleport(UUID playerUuid) {
        PendingTeleport pending = getLivePending(playerUuid);
        return pending == null ? null : new PendingTeleportTicket(pending.token(), pending.location());
    }

    /** Removes exactly the request that successfully landed; failures remain retryable. */
    public void completePendingTeleport(UUID playerUuid, PendingTeleportTicket ticket, boolean teleported) {
        if (teleported && ticket != null) {
            pendingTeleports.computeIfPresent(playerUuid,
                    (uuid, pending) -> pending.token().equals(ticket.token()) ? null : pending);
        }
    }

    private PendingTeleport getLivePending(UUID playerUuid) {
        PendingTeleport pending = pendingTeleports.get(playerUuid);
        if (pending == null) {
            return null;
        }

        if (System.currentTimeMillis() - pending.createdAt() > PENDING_TELEPORT_TTL_MILLIS) {
            pendingTeleports.remove(playerUuid, pending);
            return null;
        }
        return pending;
    }

    public record PendingTeleportTicket(UUID token, Location location) {
    }

    private record PendingTeleport(UUID token, Location location, long createdAt) {
    }
}
