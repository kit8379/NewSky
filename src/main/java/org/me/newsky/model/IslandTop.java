package org.me.newsky.model;

import java.util.Set;
import java.util.UUID;

public class IslandTop {

    private final UUID islandUuid;
    private final UUID ownerUuid;
    private final int level;
    private final Set<UUID> members;

    public IslandTop(UUID islandUuid, UUID ownerUuid, int level, Set<UUID> members) {
        this.islandUuid = islandUuid;
        this.ownerUuid = ownerUuid;
        this.level = level;
        this.members = members;
    }

    public UUID getIslandUuid() {
        return islandUuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public int getLevel() {
        return level;
    }

    public Set<UUID> getMembers() {
        return members;
    }
}