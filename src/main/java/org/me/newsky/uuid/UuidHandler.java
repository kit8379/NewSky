package org.me.newsky.uuid;

import org.me.newsky.NewSky;
import org.me.newsky.cache.DataCache;

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

    /**
     * Updates the player UUID-to-name mapping in dataCache and database asynchronously.
     *
     * @param uuid The player's UUID.
     * @param name The player's current name.
     * @return A future that completes when the update finishes.
     */
    public CompletableFuture<Void> updatePlayerUuid(UUID uuid, String name) {
        return CompletableFuture.runAsync(() -> dataCache.updatePlayerUuid(uuid, name), plugin.getBukkitAsyncExecutor());
    }

    /**
     * Gets the UUID of a player from their name (case-insensitive).
     *
     * @param name The player name.
     * @return A future containing an Optional with the UUID if found.
     */
    public CompletableFuture<Optional<UUID>> getPlayerUuid(String name) {
        return CompletableFuture.supplyAsync(() -> dataCache.getPlayerUuid(name), plugin.getBukkitAsyncExecutor());
    }

    /**
     * Gets the most recent known name of a player from their UUID.
     *
     * @param uuid The player UUID.
     * @return A future containing an Optional with the name if found.
     */
    public CompletableFuture<Optional<String>> getPlayerName(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> dataCache.getPlayerName(uuid), plugin.getBukkitAsyncExecutor());
    }
}