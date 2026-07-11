package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;
import org.me.newsky.exceptions.PlayerNotCoopedException;
import org.me.newsky.network.IslandDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoopHandler {

    private final NewSky plugin;
    private final DataCache dataCache;
    private final IslandDistributor islandDistributor;

    public CoopHandler(NewSky plugin, DataCache dataCache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.dataCache = dataCache;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> coopPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (dataCache.isPlayerCooped(islandUuid, playerUuid)) {
                throw new PlayerAlreadyCoopedException();
            }

            if (dataCache.getIslandPlayers(islandUuid).contains(playerUuid)) {
                throw new CannotCoopIslandPlayerException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.addCoop(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> unCoopPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!dataCache.isPlayerCooped(islandUuid, playerUuid)) {
                throw new PlayerNotCoopedException();
            }
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.removeCoop(islandUuid, playerUuid));
    }

    public CompletableFuture<Void> deleteAllCoopOfPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getPlayerCoopedIslands(playerUuid), plugin.getBukkitAsyncExecutor()).thenCompose(touchedIslands -> {
            CompletableFuture<?>[] futures = touchedIslands.stream().map(islandUuid -> {
                return islandDistributor.removeCoop(islandUuid, playerUuid);
            }).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(futures);
        });
    }

    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.isPlayerCooped(islandUuid, playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getCoopedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getCoopedPlayers(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}
