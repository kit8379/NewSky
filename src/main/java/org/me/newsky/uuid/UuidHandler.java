package org.me.newsky.uuid;

import org.me.newsky.NewSky;
import org.me.newsky.cache.Cache;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UuidHandler {

    private final NewSky plugin;
    private final Cache cache;

    public UuidHandler(NewSky plugin, Cache cache) {
        this.plugin = plugin;
        this.cache = cache;
    }

    /**
     * Updates the player UUID-to-name mapping in cache and database asynchronously.
     *
     * @param uuid The player's UUID.
     * @param name The player's current name.
     */
    public void updatePlayerUuid(UUID uuid, String name) {
        CompletableFuture.runAsync(() -> cache.updatePlayerUuid(uuid, name), plugin.getBukkitAsyncExecutor());
    }

    /**
     * Gets the UUID of a player from their name (case-insensitive).
     *
     * @param name The player name.
     * @return An Optional containing the UUID if found.
     */
    public Optional<UUID> getPlayerUuid(String name) {
        return cache.getPlayerUuid(name);
    }

    /**
     * Gets the most recent known name of a player from their UUID.
     *
     * @param uuid The player UUID.
     * @return An Optional containing the name if found.
     */
    public Optional<String> getPlayerName(UUID uuid) {
        return cache.getPlayerName(uuid);
    }
}
