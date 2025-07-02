package org.me.newsky.model;

import java.util.UUID;

public class IslandHome {
    private final UUID islandUuid;
    private final UUID playerUuid;
    private final String homeName;
    private final String homeLocation;

    public IslandHome(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation) {
        this.islandUuid = islandUuid;
        this.playerUuid = playerUuid;
        this.homeName = homeName;
        this.homeLocation = homeLocation;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getHomeName() {
        return homeName;
    }

    public String getHomeLocation() {
        return homeLocation;
    }
}
