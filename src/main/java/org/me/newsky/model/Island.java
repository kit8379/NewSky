package org.me.newsky.model;

import java.util.Set;
import java.util.UUID;

public final class Island {

    private final UUID islandUuid;
    private final boolean lock;
    private final boolean pvp;
    private final UUID owner;
    private final Set<UUID> members;
    private final Set<UUID> coops;
    private final Set<UUID> bans;

    public Island(UUID islandUuid, boolean lock, boolean pvp, UUID owner, Set<UUID> members, Set<UUID> coops, Set<UUID> bans) {
        this.islandUuid = islandUuid;
        this.lock = lock;
        this.pvp = pvp;
        this.owner = owner;
        this.members = Set.copyOf(members);
        this.coops = Set.copyOf(coops);
        this.bans = Set.copyOf(bans);
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

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public Set<UUID> getCoops() {
        return coops;
    }

    public Set<UUID> getBans() {
        return bans;
    }
}
