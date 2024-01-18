package org.me.newsky.teleport;

import org.bukkit.Location;
import org.me.newsky.NewSky;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportManager {

    private final NewSky plugin;
    private final ConcurrentHashMap<UUID, Location> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportManager(NewSky plugin) {
        this.plugin = plugin;
    }

    public void addPendingTeleport(UUID playerId, Location location) {
        pendingTeleports.put(playerId, location);
        plugin.debug("Added pending teleport for " + playerId);
    }

    public Location getPendingTeleport(UUID playerId) {
        return pendingTeleports.get(playerId);
    }

    public void removePendingTeleport(UUID playerId) {
        pendingTeleports.remove(playerId);
        plugin.debug("Removed pending teleport for " + playerId);
    }
}
