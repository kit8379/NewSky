package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.model.Actor;
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
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public WarpHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor, OnlinePlayerRegistry onlinePlayerRegistry) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    public CompletableFuture<Void> setWarp(Actor actor, UUID islandUuid, String warpName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return CompletableFuture.runAsync(() -> {
            if (!islandUuid.equals(IslandUtils.parseIslandUuid(worldName))) {
                throw new LocationNotInIslandException();
            }

            String normalizedWarpName = warpName.toLowerCase(Locale.ROOT);
            if (!IslandUtils.isLegalPointName(normalizedWarpName)) {
                throw new WarpNameNotLegalException();
            }

            String warpLocation = x + "," + y + "," + z + "," + yaw + "," + pitch;

            database.updateWarpPoint(actor, islandUuid, normalizedWarpName, warpLocation);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> delWarp(Actor actor, UUID islandUuid, String warpName) {
        return CompletableFuture.runAsync(() -> {
            database.deleteWarpPoint(actor, islandUuid, warpName.toLowerCase(Locale.ROOT));
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> warp(UUID islandUuid, String warpName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!onlinePlayerRegistry.isOnline(targetPlayerUuid)) {
                throw new PlayerNotOnlineException();
            }

            if (database.getIslandBans(islandUuid).contains(targetPlayerUuid)) {
                throw new PlayerBannedException();
            }

            if (database.isIslandLock(islandUuid)) {
                boolean allowed = database.getIslandPlayers(islandUuid).containsKey(targetPlayerUuid) || database.getIslandCoops(islandUuid).contains(targetPlayerUuid);
                if (!allowed) {
                    throw new IslandLockedException();
                }
            }

            return Optional.ofNullable(database.getIslandWarps(islandUuid).get(warpName.toLowerCase(Locale.ROOT))).orElseThrow(WarpDoesNotExistException::new);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(warpLocation -> islandDistributor.teleportIsland(islandUuid, targetPlayerUuid, IslandUtils.parseIslandName(islandUuid), warpLocation));
    }

    public CompletableFuture<Set<String>> getWarpNames(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> database.getIslandWarps(islandUuid).keySet(), plugin.getBukkitAsyncExecutor());
    }
}
