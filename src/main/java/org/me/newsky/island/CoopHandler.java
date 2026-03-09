package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;
import org.me.newsky.exceptions.PlayerNotCoopedException;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoopHandler {

    private final NewSky plugin;
    private final DataCache dataCache;

    public CoopHandler(NewSky plugin, DataCache dataCache) {
        this.plugin = plugin;
        this.dataCache = dataCache;
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
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> unCoopPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!dataCache.isPlayerCooped(islandUuid, playerUuid)) {
                throw new PlayerNotCoopedException();
            }

            dataCache.deleteCoopPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> deleteAllCoopOfPlayer(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> dataCache.deleteAllCoopOfPlayer(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isPlayerCooped(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.isPlayerCooped(islandUuid, playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getCoopedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getCoopedPlayers(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}