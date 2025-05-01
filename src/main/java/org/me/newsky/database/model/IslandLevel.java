package org.me.newsky.database.model;

import java.util.UUID;

public class IslandLevel {
    private final UUID islandUuid;
    private final int level;

    public IslandLevel(UUID islandUuid, int level) {
        this.islandUuid = islandUuid;
        this.level = level;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public int getLevel() {
        return level;
    }
}
