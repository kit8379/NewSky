package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;
import org.me.newsky.exceptions.PlayerNotCoopedException;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CoopHandler {

    private final NewSky plugin;
    private final Cache cache;

    public CoopHandler(NewSky plugin, Cache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    public CompletableFuture<Void> coopPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (cache.isPlayerCooped(islandUuid, playerUuid)) {
                throw new PlayerAlreadyCoopedException();
            }

            if (cache.getIslandPlayers(islandUuid).contains(playerUuid)) {
                throw new CannotCoopIslandPlayerException();
            }

            cache.updateCoopPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Void> unCoopPlayer(UUID islandUuid, UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            if (!cache.isPlayerCooped(islandUuid, playerUuid)) {
                throw new PlayerNotCoopedException();
            }
            cache.deleteCoopPlayer(islandUuid, playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }

    public void deleteAllCoopOfPlayer(UUID playerUuid) {
        CompletableFuture.runAsync(() -> cache.deleteAllCoopOfPlayer(playerUuid), plugin.getBukkitAsyncExecutor());
    }

    public Set<UUID> getCoopedPlayers(UUID islandUuid) {
        return cache.getCoopedPlayers(islandUuid);
    }
}
