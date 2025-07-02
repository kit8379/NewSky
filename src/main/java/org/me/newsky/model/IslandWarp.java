package org.me.newsky.model;

import java.util.UUID;

public class IslandWarp {
    private final UUID islandUuid;
    private final UUID playerUuid;
    private final String warpName;
    private final String warpLocation;

    public IslandWarp(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation) {
        this.islandUuid = islandUuid;
        this.playerUuid = playerUuid;
        this.warpName = warpName;
        this.warpLocation = warpLocation;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getWarpName() {
        return warpName;
    }

    public String getWarpLocation() {
        return warpLocation;
    }
}
