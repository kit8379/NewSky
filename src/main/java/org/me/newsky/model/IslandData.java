package org.me.newsky.model;

import java.util.UUID;

public class IslandData {
    private final UUID islandUuid;
    private final boolean lock;
    private final boolean pvp;

    public IslandData(UUID islandUuid, boolean lock, boolean pvp) {
        this.islandUuid = islandUuid;
        this.lock = lock;
        this.pvp = pvp;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public boolean isLock() {
        return lock;
    }

    public boolean isPvp() {
        return pvp;
    }
}
