package org.me.newsky.message;

import net.kyori.adventure.text.Component;
import org.me.newsky.NewSky;
import org.me.newsky.broker.PlayerMessageBroker;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerMessageHandler {

    private final NewSky plugin;
    private PlayerMessageBroker playerMessageBroker;

    public PlayerMessageHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public void setPlayerMessageBroker(PlayerMessageBroker playerMessageBroker) {
        this.playerMessageBroker = playerMessageBroker;
    }

    public void sendPlayerMessage(UUID playerUuid, Component message) {
        CompletableFuture.runAsync(() -> {
            playerMessageBroker.sendPlayerMessage(playerUuid, message);
            plugin.debug("PlayerMessageHandler", "Sent message to player " + playerUuid);
        }, plugin.getBukkitAsyncExecutor());
    }
}