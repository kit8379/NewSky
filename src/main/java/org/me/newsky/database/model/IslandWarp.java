package org.me.newsky.database.model;

import java.util.UUID;

public class IslandWarp {
    private final UUID playerUuid;
    private final UUID islandUuid;
    private final String warpName;
    private final String warpLocation;

    public IslandWarp(UUID playerUuid, UUID islandUuid, String warpName, String warpLocation) {
        this.playerUuid = playerUuid;
        this.islandUuid = islandUuid;
        this.warpName = warpName;
        this.warpLocation = warpLocation;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public String getWarpName() {
        return warpName;
    }

    public String getWarpLocation() {
        return warpLocation;
    }
}
