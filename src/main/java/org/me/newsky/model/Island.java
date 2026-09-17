package org.me.newsky.model;

import java.util.Map;
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
    private final Map<UUID, String> defaultHomes;
    private final String defaultWarp;
    private final int sizeLevel;
    private final int generatorLevel;

    public Island(UUID islandUuid, boolean lock, boolean pvp, UUID owner, Set<UUID> members, Set<UUID> coops, Set<UUID> bans, Map<UUID, String> defaultHomes, String defaultWarp, int sizeLevel, int generatorLevel) {
        this.islandUuid = islandUuid;
        this.lock = lock;
        this.pvp = pvp;
        this.owner = owner;
        this.members = Set.copyOf(members);
        this.coops = Set.copyOf(coops);
        this.bans = Set.copyOf(bans);
        this.defaultHomes = Map.copyOf(defaultHomes);
        this.defaultWarp = defaultWarp;
        this.sizeLevel = sizeLevel;
        this.generatorLevel = generatorLevel;
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

    public Map<UUID, String> getDefaultHomes() {
        return defaultHomes;
    }

    public String getDefaultWarp() {
        return defaultWarp;
    }

    public int getSizeLevel() {
        return sizeLevel;
    }

    public int getGeneratorLevel() {
        return generatorLevel;
    }
}
