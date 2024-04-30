package org.me.newsky.teleport;

import org.bukkit.Location;
import org.me.newsky.NewSky;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportHandler {

    private final NewSky plugin;
    private final ConcurrentHashMap<UUID, Location> pendingTeleports = new ConcurrentHashMap<>();

    public TeleportHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public void addPendingTeleport(UUID playerUuid, Location location) {
        pendingTeleports.put(playerUuid, location);
        plugin.debug("Added pending teleport for " + playerUuid);
    }

    public void removePendingTeleport(UUID playerUuid) {
        pendingTeleports.remove(playerUuid);
        plugin.debug("Removed pending teleport for " + playerUuid);
    }

    public Location getPendingTeleport(UUID playerUuid) {
        return pendingTeleports.get(playerUuid);
    }
}

