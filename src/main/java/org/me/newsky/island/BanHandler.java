package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;
import org.me.newsky.exceptions.PlayerNotBannedException;
import org.me.newsky.network.distributor.IslandDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BanHandler {

    private final NewSky plugin;
    private final DataCache dataCache;
    private final IslandDistributor islandDistributor;

    public BanHandler(NewSky plugin, DataCache dataCache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.dataCache = dataCache;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (dataCache.isPlayerBanned(islandUuid, playerUuid)) {
                throw new PlayerAlreadyBannedException();
            }

            if (dataCache.getIslandPlayers(islandUuid).contains(playerUuid)) {
                throw new CannotBanIslandPlayerException();
            }

            islandDistributor.expelPlayer(islandUuid, playerUuid);
            dataCache.updateBanPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!dataCache.isPlayerBanned(islandUuid, playerUuid)) {
                throw new PlayerNotBannedException();
            }

            dataCache.deleteBanPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Boolean> isPlayerBanned(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.isPlayerBanned(islandUuid, playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Set<UUID>> getBannedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getBannedPlayers(islandUuid), plugin.getBukkitAsyncExecutor());
    }
}