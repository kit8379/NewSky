package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;
import org.me.newsky.exceptions.PlayerNotBannedException;
import org.me.newsky.island.distributor.IslandDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BanHandler {

    private final NewSky plugin;
    private final Cache cache;
    private final IslandDistributor islandDistributor;

    public BanHandler(NewSky plugin, Cache cache, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.cache = cache;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (cache.isPlayerBanned(islandUuid, playerUuid)) {
                throw new PlayerAlreadyBannedException();
            }

            if (cache.getIslandPlayers(islandUuid).contains(playerUuid)) {
                throw new CannotBanIslandPlayerException();
            }

            islandDistributor.expelPlayer(islandUuid, playerUuid);
            cache.updateBanPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!cache.isPlayerBanned(islandUuid, playerUuid)) {
                throw new PlayerNotBannedException();
            }

            cache.deleteBanPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public Set<UUID> getBannedPlayers(UUID islandUuid) {
        return cache.getBannedPlayers(islandUuid);
    }
}