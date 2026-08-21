package org.me.newsky.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable snapshot of one island's enforcement state. Every mutation produces a new instance
 * via the with-methods below, which are the delta vocabulary of the snapshot cache: a write
 * applies its own known delta to the hosted copy instead of re-reading the database, so each
 * with-method must mirror exactly what the corresponding database transaction does - and stay
 * idempotent, because a delta may be re-applied on top of a seed read that already contains it.
 */
public final class Island {

    private final UUID islandUuid;
    private final boolean lock;
    private final boolean pvp;
    private final UUID owner;
    private final Set<UUID> members;
    private final Set<UUID> coops;
    private final Set<UUID> bans;
    private final long stateVersion;

    public Island(UUID islandUuid, boolean lock, boolean pvp, UUID owner, Set<UUID> members, Set<UUID> coops, Set<UUID> bans) {
        this(islandUuid, lock, pvp, owner, members, coops, bans, 0L);
    }

    public Island(UUID islandUuid, boolean lock, boolean pvp, UUID owner, Set<UUID> members, Set<UUID> coops, Set<UUID> bans, long stateVersion) {
        this.islandUuid = islandUuid;
        this.lock = lock;
        this.pvp = pvp;
        this.owner = owner;
        this.members = Set.copyOf(members);
        this.coops = Set.copyOf(coops);
        this.bans = Set.copyOf(bans);
        this.stateVersion = stateVersion;
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

    public long getStateVersion() {
        return stateVersion;
    }

    public Island withStateVersion(long newStateVersion) {
        return new Island(islandUuid, lock, pvp, owner, members, coops, bans, newStateVersion);
    }

    public Island withLock(boolean newLock) {
        return new Island(islandUuid, newLock, pvp, owner, members, coops, bans, stateVersion);
    }

    public Island withPvp(boolean newPvp) {
        return new Island(islandUuid, lock, newPvp, owner, members, coops, bans, stateVersion);
    }

    /** Mirrors the transfer transaction: the old owner becomes a member, the new owner stops being one. */
    public Island withOwner(UUID newOwner) {
        Set<UUID> newMembers = new HashSet<>(members);
        if (owner != null && !owner.equals(newOwner)) {
            newMembers.add(owner);
        }
        newMembers.remove(newOwner);
        return new Island(islandUuid, lock, pvp, newOwner, newMembers, coops, bans, stateVersion);
    }

    /** Mirrors the add-member transaction, which also clears the joiner's ban and coop rows. */
    public Island withMemberAdded(UUID playerUuid) {
        Set<UUID> newMembers = new HashSet<>(members);
        newMembers.add(playerUuid);
        return new Island(islandUuid, lock, pvp, owner, newMembers, without(coops, playerUuid), without(bans, playerUuid), stateVersion);
    }

    public Island withMemberRemoved(UUID playerUuid) {
        return new Island(islandUuid, lock, pvp, owner, without(members, playerUuid), coops, bans, stateVersion);
    }

    public Island withBanAdded(UUID playerUuid) {
        Set<UUID> newBans = new HashSet<>(bans);
        newBans.add(playerUuid);
        return new Island(islandUuid, lock, pvp, owner, members, coops, newBans, stateVersion);
    }

    public Island withBanRemoved(UUID playerUuid) {
        return new Island(islandUuid, lock, pvp, owner, members, coops, without(bans, playerUuid), stateVersion);
    }

    public Island withCoopAdded(UUID playerUuid) {
        Set<UUID> newCoops = new HashSet<>(coops);
        newCoops.add(playerUuid);
        return new Island(islandUuid, lock, pvp, owner, members, newCoops, bans, stateVersion);
    }

    public Island withCoopRemoved(UUID playerUuid) {
        return new Island(islandUuid, lock, pvp, owner, members, without(coops, playerUuid), bans, stateVersion);
    }

    private static Set<UUID> without(Set<UUID> source, UUID playerUuid) {
        if (!source.contains(playerUuid)) {
            return source;
        }
        Set<UUID> copy = new HashSet<>(source);
        copy.remove(playerUuid);
        return copy;
    }
}
