package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.HomeNameNotLegalException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.model.Actor;
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

    /** SELF: homes belong to a player. Island membership is enforced by the foreign key below. */
    public CompletableFuture<Void> setHome(Actor actor, UUID playerUuid, String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {
        actor.requireSelf(playerUuid);

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

            database.setHome(islandUuid, playerUuid, normalizedHomeName, homeLocation);
        }, plugin.getBukkitAsyncExecutor());
    }

    /** SELF: homes belong to a player. */
    public CompletableFuture<Void> deleteHome(Actor actor, UUID playerUuid, String homeName) {
        actor.requireSelf(playerUuid);

        return CompletableFuture.runAsync(() -> {
            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);

            database.deleteHome(islandUuid, playerUuid, homeName);
        }, plugin.getBukkitAsyncExecutor());
    }

    /**
     * SELF: using someone else's home as a teleport destination - or sending a third party to it -
     * is an operator action. A player may only travel to their own homes.
     */
    public CompletableFuture<Void> teleportToHome(Actor actor, UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        actor.requireSelf(playerUuid);
        actor.requireSelf(targetPlayerUuid);

        return CompletableFuture.supplyAsync(() -> {
            UUID islandUuid = database.getIslandUuid(playerUuid).orElseThrow(IslandDoesNotExistException::new);
            String homeLocation = Optional.ofNullable(database.getIslandHomes(islandUuid, playerUuid).get(homeName)).orElseThrow(HomeDoesNotExistException::new);

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
