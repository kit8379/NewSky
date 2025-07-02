package org.me.newsky.island;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.island.distributor.IslandDistributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MessageHandler {

    private final NewSky plugin;
    private final IslandDistributor islandDistributor;

    public MessageHandler(NewSky plugin, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> sendMessage(UUID playerUuid, Component message) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> islandDistributor.sendMessage(playerUuid, message));
    }

}
