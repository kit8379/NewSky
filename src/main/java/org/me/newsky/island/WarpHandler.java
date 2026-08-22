package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.network.IslandDistributor;
import org.me.newsky.util.IslandUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WarpHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;

    public WarpHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> setWarp(UUID playerUuid, String warpName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return CompletableFuture.runAsync(() -> {
            // The island is derived from the world the point lives in. Membership of that island is
            // enforced by the island_warps to island_players foreign key, so no lookup is needed here.
            UUID islandUuid = IslandUtils.parseIslandUuid(worldName);
            if (islandUuid == null) {
                throw new LocationNotInIslandException();
            }

            String normalizedWarpName = warpName.toLowerCase(Locale.ROOT);
            if (!IslandUtils.isLegalPointName(normalizedWarpName)) {
                throw new WarpNameNotLegalException();
            }

            String warpLocation = x + "," + y + "," + z + "," + yaw + "," + pitch;

            database.updateWarpPoint(islandUuid, playerUuid, normalizedWarpName, warpLocation);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> delWarp(UUID playerUuid, String warpName) {
        return CompletableFuture.runAsync(() -> {
            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);

            database.deleteWarpPoint(islandUuid, playerUuid, warpName);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> warp(UUID warpPlayerUuid, String warpName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            UUID islandUuid = database.getIslandUuid(warpPlayerUuid).orElseThrow(IslandDoesNotExistException::new);

            // Fail-fast filters only: the same rules are re-enforced on arrival by
            // IslandAccessListener, which is what actually keeps banned or locked-out players out.
            if (database.getIslandBans(islandUuid).contains(targetPlayerUuid)) {
                throw new PlayerBannedException();
            }

            boolean isLocked = database.isIslandLock(islandUuid);
            boolean isMember = database.getIslandPlayers(islandUuid).containsKey(targetPlayerUuid);
            if (isLocked && !isMember) {
                throw new IslandLockedException();
            }

            String warpLocation = Optional.ofNullable(database.getIslandWarps(islandUuid, warpPlayerUuid).get(warpName)).orElseThrow(WarpDoesNotExistException::new);

            return new WarpTarget(islandUuid, warpLocation);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(target -> islandDistributor.teleportIsland(target.islandUuid(), targetPlayerUuid, IslandUtils.UUIDToName(target.islandUuid()), target.warpLocation()));
    }

    public CompletableFuture<Set<String>> getWarpNames(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);

            return database.getIslandWarps(islandUuid, playerUuid).keySet();
        }, plugin.getBukkitAsyncExecutor());
    }

    private record WarpTarget(UUID islandUuid, String warpLocation) {
    }
}
