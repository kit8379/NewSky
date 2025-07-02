package org.me.newsky.model;

import java.util.UUID;

public class IslandBan {
    private final UUID islandUuid;
    private final UUID bannedPlayer;

    public IslandBan(UUID islandUuid, UUID bannedPlayer) {
        this.islandUuid = islandUuid;
        this.bannedPlayer = bannedPlayer;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public UUID getBannedPlayer() {
        return bannedPlayer;
    }
}
