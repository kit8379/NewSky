package org.me.newsky.uuid;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UuidHandler {

    private final NewSky plugin;
    private final DataCache dataCache;

    public UuidHandler(NewSky plugin, DataCache dataCache) {
        this.plugin = plugin;
        this.dataCache = dataCache;
    }

    public CompletableFuture<Void> updatePlayerUuid(UUID uuid, String name) {
        return CompletableFuture.runAsync(() -> dataCache.updatePlayerUuid(uuid, name), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<UUID>> getPlayerUuid(String name) {
        return CompletableFuture.supplyAsync(() -> dataCache.getPlayerUuid(name), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<String>> getPlayerName(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getPlayerName(uuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Map<UUID, String>> getPlayerNames(Collection<UUID> uuids) {
        return CompletableFuture.supplyAsync(() -> dataCache.getPlayerNames(uuids), plugin.getBukkitAsyncExecutor());
    }
}