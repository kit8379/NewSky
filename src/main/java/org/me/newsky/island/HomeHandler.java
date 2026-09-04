package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.HomeNameNotLegalException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
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

    public HomeHandler(NewSky plugin, DatabaseHandler database, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.database = database;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {
        return CompletableFuture.runAsync(() -> {
            // The island is derived from the world the point lives in. Membership of that island is
            // enforced by the island_homes to island_players foreign key, so no lookup is needed here.
            UUID islandUuid = IslandUtils.parseIslandUuid(worldName);
            if (islandUuid == null) {
                throw new LocationNotInIslandException();
            }

            String normalizedHomeName = homeName.toLowerCase(Locale.ROOT);
            if (!IslandUtils.isLegalPointName(normalizedHomeName)) {
                throw new HomeNameNotLegalException();
            }

            String homeLocation = x + "," + y + "," + z + "," + yaw + "," + pitch;

            database.updateHomePoint(islandUuid, playerUuid, normalizedHomeName, homeLocation);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return CompletableFuture.runAsync(() -> {
            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);

            database.deleteHomePoint(islandUuid, playerUuid, homeName);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
            String homeLocation = Optional.ofNullable(database.getIslandHomes(islandUuid, playerUuid).get(homeName.toLowerCase(Locale.ROOT))).orElseThrow(HomeDoesNotExistException::new);

            return new HomeTarget(islandUuid, homeLocation);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(target -> islandDistributor.teleportIsland(target.islandUuid(), targetPlayerUuid, IslandUtils.UUIDToName(target.islandUuid()), target.homeLocation()));
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
