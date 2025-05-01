package org.me.newsky.cache;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class CacheHandler {

    // ----------------------------------------
    // Initialization
    // ----------------------------------------

    public final void cacheAllData() {
        flushAllData();
        cacheIslandData();
        cacheIslandPlayers();
        cacheIslandHomes();
        cacheIslandWarps();
        cacheIslandLevels();
        cacheIslandBans();
    }

    protected abstract void flushAllData();

    protected abstract void cacheIslandData();

    protected abstract void cacheIslandPlayers();

    protected abstract void cacheIslandHomes();

    protected abstract void cacheIslandWarps();

    protected abstract void cacheIslandLevels();

    protected abstract void cacheIslandBans();


    // ----------------------------------------
    // Island Data Operations
    // ----------------------------------------

    public abstract void createIsland(UUID islandUuid);

    public abstract void deleteIsland(UUID islandUuid);

    public abstract void updateIslandLock(UUID islandUuid, boolean lock);

    public abstract void updateIslandPvp(UUID islandUuid, boolean pvp);

    public abstract boolean getIslandLock(UUID islandUuid);

    public abstract boolean getIslandPvp(UUID islandUuid);

    // ----------------------------------------
    // Island Players
    // ----------------------------------------

    public abstract void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role);

    public abstract void deleteIslandPlayer(UUID islandUuid, UUID playerUuid);

    public abstract void updateIslandOwner(UUID islandUuid, UUID playerUuid);

    public abstract UUID getIslandOwner(UUID islandUuid);

    public abstract Set<UUID> getIslandMembers(UUID islandUuid);

    public abstract Set<UUID> getIslandPlayers(UUID islandUuid);

    public abstract Optional<UUID> getIslandUuid(UUID playerUuid);

    // ----------------------------------------
    // Homes
    // ----------------------------------------

    public abstract void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation);

    public abstract void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName);

    public abstract Optional<String> getHomeLocation(UUID islandUuid, UUID playerUuid, String homeName);

    public abstract Set<String> getHomeNames(UUID islandUuid, UUID playerUuid);

    // ----------------------------------------
    // Warps
    // ----------------------------------------

    public abstract void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation);

    public abstract void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName);

    public abstract Optional<String> getWarpLocation(UUID islandUuid, UUID playerUuid, String warpName);

    public abstract Set<String> getWarpNames(UUID islandUuid, UUID playerUuid);

    // ----------------------------------------
    // Island Level
    // ----------------------------------------

    public abstract void updateIslandLevel(UUID islandUuid, int level);

    public abstract int getIslandLevel(UUID islandUuid);

    public abstract Map<UUID, Integer> getTopIslandLevels(int size);

    // ----------------------------------------
    // Bans
    // ----------------------------------------

    public abstract void updateBanPlayer(UUID islandUuid, UUID playerUuid);

    public abstract void deleteBanPlayer(UUID islandUuid, UUID playerUuid);

    public abstract boolean getPlayerBanned(UUID islandUuid, UUID playerUuid);

    public abstract Set<UUID> getBannedPlayers(UUID islandUuid);

    // ----------------------------------------
    // Server Heartbeats
    // ----------------------------------------

    public abstract void updateActiveServer(String serverName);

    public abstract void removeActiveServer(String serverName);

    public abstract Map<String, String> getActiveServers();

    // ----------------------------------------
    // Island Load Info
    // ----------------------------------------

    public abstract void updateIslandLoadedServer(UUID islandUuid, String serverName);

    public abstract void removeIslandLoadedServer(UUID islandUuid);

    public abstract String getIslandLoadedServer(UUID islandUuid);

    // ----------------------------------------
    // Online Players
    // ----------------------------------------

    public abstract void addOnlinePlayer(String playerName, String serverName);

    public abstract void removeOnlinePlayer(String playerName, String serverName);

    public abstract Set<String> getOnlinePlayers();
}