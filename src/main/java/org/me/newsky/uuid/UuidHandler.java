package org.me.newsky.uuid;

import org.me.newsky.NewSky;
import org.me.newsky.database.DatabaseHandler;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class UuidHandler {

    private final NewSky plugin;
    private final DatabaseHandler database;

    public UuidHandler(NewSky plugin, DatabaseHandler database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<Void> updatePlayerUuid(UUID uuid, String name) {
        return CompletableFuture.runAsync(() -> database.updatePlayerName(uuid, name), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<UUID>> getPlayerUuid(String name) {
        return CompletableFuture.supplyAsync(() -> database.getPlayerUuid(name), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Optional<String>> getPlayerName(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> database.getPlayerName(uuid), plugin.getBukkitAsyncExecutor());
    }

    public CompletableFuture<Map<UUID, String>> getPlayerNames(Collection<UUID> uuids) {
        return CompletableFuture.supplyAsync(() -> database.getPlayerNames(uuids), plugin.getBukkitAsyncExecutor());
    }
}
