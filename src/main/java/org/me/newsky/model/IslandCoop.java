package org.me.newsky.model;

import java.util.UUID;

public class IslandCoop {
    private final UUID islandUuid;
    private final UUID coopedPlayer;

    public IslandCoop(UUID islandUuid, UUID coopedPlayer) {
        this.islandUuid = islandUuid;
        this.coopedPlayer = coopedPlayer;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public UUID getCoopedPlayer() {
        return coopedPlayer;
    }
}
