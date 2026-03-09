package org.me.newsky.message;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.me.newsky.NewSky;
import org.me.newsky.broker.PlayerMessageBroker;
import org.me.newsky.cache.RuntimeCache;

import java.util.UUID;

public class PlayerMessageHandler {

    private final NewSky plugin;
    private final RuntimeCache runtimeCache;
    private PlayerMessageBroker playerMessageBroker;

    public PlayerMessageHandler(NewSky plugin, RuntimeCache runtimeCache) {
        this.plugin = plugin;
        this.runtimeCache = runtimeCache;
    }

    public void setPlayerMessageBroker(PlayerMessageBroker playerMessageBroker) {
        this.playerMessageBroker = playerMessageBroker;
    }

    public void sendPlayerMessage(UUID playerUuid, Component message) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            runtimeCache.getPlayerOnlineServer(playerUuid).ifPresent(playerServer -> {
                playerMessageBroker.sendPlayerMessage(playerServer, playerUuid, message);
                plugin.debug("PlayerMessageHandler", "Sent message to player " + playerUuid + " on server " + playerServer);
            });
        });
    }
}