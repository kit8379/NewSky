package org.me.newsky.teleport;

import org.bukkit.Location;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class TeleportManager {

    private final Logger logger;
    private final ConcurrentHashMap<UUID, Location> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportManager(Logger logger) {
        this.logger = logger;
    }

    public void addPendingTeleport(UUID playerId, Location location) {
        pendingTeleports.put(playerId, location);
        logger.info("Added pending teleport for " + playerId);
    }

    public Location getPendingTeleport(UUID playerId) {
        return pendingTeleports.get(playerId);
    }

    public void removePendingTeleport(UUID playerId) {
        pendingTeleports.remove(playerId);
        logger.info("Removed pending teleport for " + playerId);
    }
}
