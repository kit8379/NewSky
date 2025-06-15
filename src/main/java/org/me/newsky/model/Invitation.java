package org.me.newsky.model;

import java.util.UUID;

public class Invitation {
    private final UUID islandUuid;
    private final UUID inviterUuid;

    public Invitation(UUID islandUuid, UUID inviterUuid) {
        this.islandUuid = islandUuid;
        this.inviterUuid = inviterUuid;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public UUID getInviterUuid() {
        return inviterUuid;
    }
}
