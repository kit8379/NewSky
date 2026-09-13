package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.*;
import org.me.newsky.network.IslandDistributor;
import org.me.newsky.util.IslandUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HomeHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;
    private final IslandDistributor islandDistributor;
    private final OnlinePlayerRegistry onlinePlayerRegistry;

    public HomeHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor, OnlinePlayerRegistry onlinePlayerRegistry) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
    }

    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return CompletableFuture.supplyAsync(() -> {
            UUID islandUuid = IslandUtils.parseIslandUuid(worldName);
            if (islandUuid == null) {
                throw new LocationNotInIslandException();
            }

            String normalizedHomeName = homeName.toLowerCase(Locale.ROOT);
            if (!IslandUtils.isLegalPointName(normalizedHomeName)) {
                throw new HomeNameNotLegalException();
            }

            String homeLocation = x + "," + y + "," + z + "," + yaw + "," + pitch;

            return islandDistributor.setHome(islandUuid, playerUuid, normalizedHomeName, homeLocation);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(future -> future);
    }

    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return CompletableFuture.supplyAsync(() -> {
            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);

            return islandDistributor.deleteHome(islandUuid, playerUuid, homeName.toLowerCase(Locale.ROOT));
        }, plugin.getBukkitAsyncExecutor()).thenCompose(future -> future);
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!onlinePlayerRegistry.isOnline(targetPlayerUuid)) {
                throw new PlayerNotOnlineException();
            }

            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
            String homeLocation = Optional.ofNullable(database.getIslandHomes(islandUuid, playerUuid).get(homeName.toLowerCase(Locale.ROOT))).orElseThrow(HomeDoesNotExistException::new);

            return new HomeTarget(islandUuid, homeLocation);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(target -> islandDistributor.teleportIsland(target.islandUuid(), targetPlayerUuid, IslandUtils.parseIslandName(target.islandUuid()), target.homeLocation()));
    }

    public CompletableFuture<Set<String>> getHomeNames(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);

            return database.getIslandHomes(islandUuid, playerUuid).keySet();
        }, plugin.getBukkitAsyncExecutor());
    }

    private record HomeTarget(UUID islandUuid, String homeLocation) {
    }
}
