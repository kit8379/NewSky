package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;
import org.me.newsky.exceptions.PlayerNotCoopedException;
import org.me.newsky.network.distributor.IslandDistributor;

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

            dataCache.updateCoopPlayer(islandUuid, playerUuid);
            islandDistributor.reloadSnapshot(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> unCoopPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!dataCache.isPlayerCooped(islandUuid, playerUuid)) {
                throw new PlayerNotCoopedException();
            }

            dataCache.deleteCoopPlayer(islandUuid, playerUuid);
            islandDistributor.reloadSnapshot(islandUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> deleteAllCoopOfPlayer(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> touchedIslands = dataCache.deleteAllCoopOfPlayer(playerUuid);
            for (UUID islandUuid : touchedIslands) {
                islandDistributor.reloadSnapshot(islandUuid);
            }
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.isPlayerCooped(islandUuid, playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getCoopedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getCoopedPlayers(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}