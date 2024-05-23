package org.me.newsky.island;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;
import org.me.newsky.exceptions.PlayerNotBannedException;
import org.me.newsky.island.middleware.PreIslandHandler;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BanHandler {

    private final CacheHandler cacheHandler;
    private final PreIslandHandler preIslandHandler;

    public BanHandler(CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.cacheHandler = cacheHandler;
        this.preIslandHandler = preIslandHandler;
    }

    public CompletableFuture<Void> banPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (cacheHandler.getPlayerBanned(islandUuid, playerUuid)) {
                throw new PlayerAlreadyBannedException();
            }
            if (cacheHandler.getIslandPlayers(islandUuid).contains(playerUuid)) {
                throw new CannotBanIslandPlayerException();
            }
            preIslandHandler.expelPlayer(islandUuid, playerUuid);
            cacheHandler.updateBanPlayer(islandUuid, playerUuid);
            return null;
        });
    }

    public CompletableFuture<Void> unbanPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            if (!cacheHandler.getPlayerBanned(islandUuid, playerUuid)) {
                throw new PlayerNotBannedException();
            }
            cacheHandler.deleteBanPlayer(islandUuid, playerUuid);
            return null;
        });
    }

    public CompletableFuture<Set<UUID>> getBannedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getBannedPlayers(islandUuid));
    }

}
