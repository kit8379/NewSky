package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;
import org.me.newsky.exceptions.PlayerNotBannedException;
import org.me.newsky.island.middleware.IslandServiceDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BanHandler {

    private final NewSky plugin;
    private final CacheHandler cacheHandler;
    private final IslandServiceDistributor islandServiceDistributor;

    public BanHandler(NewSky plugin, CacheHandler cacheHandler, IslandServiceDistributor islandServiceDistributor) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
        this.islandServiceDistributor = islandServiceDistributor;
    }

    public CompletableFuture<Set<UUID>> getBannedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getBannedPlayers(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (cacheHandler.isPlayerBanned(islandUuid, playerUuid)) {
                throw new PlayerAlreadyBannedException();
            }
            if (cacheHandler.getIslandPlayers(islandUuid).contains(playerUuid)) {
                throw new CannotBanIslandPlayerException();
            }
            islandServiceDistributor.expelPlayer(islandUuid, playerUuid);
            cacheHandler.updateBanPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!cacheHandler.isPlayerBanned(islandUuid, playerUuid)) {
                throw new PlayerNotBannedException();
            }
            cacheHandler.deleteBanPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}
