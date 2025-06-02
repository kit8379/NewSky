package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.exceptions.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoopHandler {


    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public CoopHandler(NewSky plugin, CacheHandler cacheHandler) {
        this.plugin = plugin;
        this.cacheHandler = cacheHandler;
    }

    public CompletableFuture<Set<UUID>> getCoopedPlayers(UUID islandUuid) {
        return CompletableFuture.supplyAsync(() -> cacheHandler.getCoopedPlayers(islandUuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> addCoop(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (cacheHandler.isPlayerCooped(islandUuid, playerUuid)) {
                throw new PlayerAlreadyCoopedException();
            }

            if (cacheHandler.getIslandPlayers(islandUuid).contains(playerUuid)) {
                throw new CannotCoopIslandPlayerException();
            }

            cacheHandler.addCoopPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }


    public CompletableFuture<Void> removeCoop(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!cacheHandler.isPlayerCooped(islandUuid, playerUuid)) {
                throw new PlayerNotCoopedException();
            }
            cacheHandler.removeCoopPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public void removeAllCoopOfPlayer(UUID playerUuid) {
        CompletableFuture.runAsync(() -> cacheHandler.removeAllCoopOfPlayer(playerUuid), plugin.getBukkitAsyncExecutor());
    }
}
