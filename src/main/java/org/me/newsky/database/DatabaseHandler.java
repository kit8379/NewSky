package org.me.newsky.database;

import org.me.newsky.database.model.*;

import java.util.List;
import java.util.UUID;

public abstract class DatabaseHandler {

    // ----------------------------------------
    // Lifecycle
    // ----------------------------------------

    public abstract void close();

    public abstract void createTables();

    // ----------------------------------------
    // SELECT
    // ----------------------------------------

    public abstract List<IslandData> selectAllIslandData();

    public abstract List<IslandPlayer> selectAllIslandPlayers();

    public abstract List<IslandHome> selectAllIslandHomes();

    public abstract List<IslandWarp> selectAllIslandWarps();

    public abstract List<IslandLevel> selectAllIslandLevels();

    public abstract List<IslandBan> selectAllIslandBans();

    // ----------------------------------------
    // INSERT / UPDATE
    // ----------------------------------------

    public abstract void addIslandData(UUID islandUuid);

    public abstract void updateIslandPlayer(UUID islandUuid, UUID playerUuid, String role);

    public abstract void updateHomePoint(UUID islandUuid, UUID playerUuid, String homeName, String homeLocation);

    public abstract void updateWarpPoint(UUID islandUuid, UUID playerUuid, String warpName, String warpLocation);

    public abstract void updateIslandLock(UUID islandUuid, boolean lock);

    public abstract void updateIslandPvp(UUID islandUuid, boolean pvp);

    public abstract void updateIslandOwner(UUID islandUuid, UUID playerUuid);

    public abstract void updateIslandLevel(UUID islandUuid, int level);

    public abstract void updateBanPlayer(UUID islandUuid, UUID playerUuid);

    // ----------------------------------------
    // DELETE
    // ----------------------------------------

    public abstract void deleteIsland(UUID islandUuid);

    public abstract void deleteIslandPlayer(UUID islandUuid, UUID playerUuid);

    public abstract void deleteHomePoint(UUID islandUuid, UUID playerUuid, String homeName);

    public abstract void deleteWarpPoint(UUID islandUuid, UUID playerUuid, String warpName);

    public abstract void deleteBanPlayer(UUID islandUuid, UUID playerUuid);
}
