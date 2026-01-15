package org.me.newsky.message;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.network.distributor.Distributor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MessageHandler {

    private final NewSky plugin;
    private final Distributor distributor;

    public MessageHandler(NewSky plugin, Distributor distributor) {
        this.plugin = plugin;
        this.distributor = distributor;
    }

    public CompletableFuture<Void> sendMessage(UUID playerUuid, Component message) {
        return CompletableFuture.runAsync(() -> {
        }, plugin.getBukkitAsyncExecutor()).thenCompose(v -> distributor.sendMessage(playerUuid, message));
    }

}
