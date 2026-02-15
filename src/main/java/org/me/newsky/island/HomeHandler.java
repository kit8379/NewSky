// HomeHandler.java
package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
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
    private final Cache cache;
    private final IslandDistributor islandDistributor;

    public HomeHandler(NewSky plugin, Cache cache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.cache = cache;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> setHome(UUID playerUuid, String homeName, String worldName, double x, double y, double z, float yaw, float pitch) {

        return CompletableFuture.supplyAsync(() -> cache.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
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

            cache.updateHomePoint(islandUuid, playerUuid, normalizedHomeName, homeLocation);

            return CompletableFuture.completedFuture(null);
        });
    }


    public CompletableFuture<Void> delHome(UUID playerUuid, String homeName) {
        return CompletableFuture.supplyAsync(() -> cache.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            UUID islandUuid = islandUuidOpt.get();
            if (cache.getHomeLocation(islandUuid, playerUuid, homeName).isEmpty()) {
                throw new HomeDoesNotExistException();
            }

            cache.deleteHomePoint(islandUuid, playerUuid, homeName);
            return CompletableFuture.completedFuture(null);
        });
    }

    public CompletableFuture<Void> home(UUID playerUuid, String homeName, UUID targetPlayerUuid) {
        return CompletableFuture.supplyAsync(() -> cache.getIslandUuid(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(islandUuidOpt -> {
            if (islandUuidOpt.isEmpty()) {
                throw new IslandDoesNotExistException();
            }

            UUID islandUuid = islandUuidOpt.get();
            Optional<String> homeLocationOpt = cache.getHomeLocation(islandUuid, playerUuid, homeName);
            if (homeLocationOpt.isEmpty()) {
                throw new HomeDoesNotExistException();
            }

            String homeWorld = IslandUtils.UUIDToName(islandUuid);
            String homeLocation = homeLocationOpt.get();

            return islandDistributor.teleportIsland(islandUuid, targetPlayerUuid, homeWorld, homeLocation);
        });
    }

    public Set<String> getHomeNames(UUID playerUuid) {
        Optional<UUID> islandUuidOpt = cache.getIslandUuid(playerUuid);
        if (islandUuidOpt.isEmpty()) {
            throw new IslandDoesNotExistException();
        }

        UUID islandUuid = islandUuidOpt.get();
        return cache.getHomeNames(islandUuid, playerUuid);
    }
}
