package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.exceptions.HomeDoesNotExistException;
import org.me.newsky.exceptions.HomeNameNotLegalException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;
import org.me.newsky.network.distributor.IslandDistributor;
import org.me.newsky.util.IslandUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class HomeHandler {

    private final NewSky plugin;
    private final DataCache dataCache;
    private final IslandDistributor islandDistributor;

    public HomeHandler(NewSky plugin, DataCache dataCache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.dataCache = dataCache;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {

        return CompletableFuture.supplyAsync(() -> dataCache.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            UUID islandUuid = islandUuidOpt.get();

            if (!worldName.equals(IslandUtils.UUIDToName(islandUuid))) {
                throw new LocationNotInIslandException();
            }

            String normalizedHomeName = homeName.toLowerCase(Locale.ROOT);

            if (normalizedHomeName.isEmpty() || normalizedHomeName.length() > 32) {
                throw new HomeNameNotLegalException();
            }

            for (int i = 0; i < normalizedHomeName.length(); i++) {
                char c = normalizedHomeName.charAt(i);
                if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-') {
                    throw new HomeNameNotLegalException();
                }
            }

            String homeLocation = x + "," + y + "," + z + "," + yaw + "," + pitch;

            dataCache.updateHomePoint(islandUuid, playerUuid, normalizedHomeName, homeLocation);

            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return CompletableFuture.supplyAsync(() -> dataCache.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            UUID islandUuid = islandUuidOpt.get();
            if (dataCache.getHomeLocation(islandUuid, playerUuid, homeName).isEmpty()) {
                throw new HomeDoesNotExistException();
            }

            dataCache.deleteHomePoint(islandUuid, playerUuid, homeName);

            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            return dataCache.getIslandUuid(playerUuid);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            UUID islandUuid = islandUuidOpt.get();
            Optional<String> homeLocationOpt = dataCache.getHomeLocation(islandUuid, playerUuid, homeName);
            if (homeLocationOpt.isEmpty()) {
                throw new HomeDoesNotExistException();
            }

            String homeWorld = IslandUtils.UUIDToName(islandUuid);
            String homeLocation = homeLocationOpt.get();

            return islandDistributor.teleportIsland(islandUuid, targetPlayerUuid, homeWorld, homeLocation);
        });
    }

    public CompletableFuture<Set<String>> getHomeNames(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<UUID> islandUuidOpt = dataCache.getIslandUuid(playerUuid);
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            UUID islandUuid = islandUuidOpt.get();
            return dataCache.getHomeNames(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}