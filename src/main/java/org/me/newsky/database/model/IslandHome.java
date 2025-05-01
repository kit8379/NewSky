package org.me.newsky.database.model;

import java.util.UUID;

public class IslandHome {
    private final UUID playerUuid;
    private final UUID islandUuid;
    private final String homeName;
    private final String homeLocation;

    public IslandHome(UUID playerUuid, UUID islandUuid, String homeName, String homeLocation) {
        this.playerUuid = playerUuid;
        this.islandUuid = islandUuid;
        this.homeName = homeName;
        this.homeLocation = homeLocation;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public String getHomeName() {
        return homeName;
    }

    public String getHomeLocation() {
        return homeLocation;
    }
}
