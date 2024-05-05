package org.me.newsky.teleport;

import org.bukkit.Location;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportHandler {

    private final ConcurrentHashMap<UUID, Location> pendingTeleports = new ConcurrentHashMap<>();

    public void addPendingTeleport(UUID playerUuid, Location location) {
        pendingTeleports.put(playerUuid, location);
    }

    public void removePendingTeleport(UUID playerUuid) {
        pendingTeleports.remove(playerUuid);
    }

    public Location getPendingTeleport(UUID playerUuid) {
        return pendingTeleports.get(playerUuid);
    }
}

