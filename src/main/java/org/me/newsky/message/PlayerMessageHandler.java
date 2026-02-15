// ============================================================
// PlayerMessageHandler.java
// - Truly fire-and-forget
// - No CompletableFuture returned
// - No extra async hop here (broker already schedules correctly)
// ============================================================

package org.me.newsky.message;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.me.newsky.NewSky;
import org.me.newsky.broker.PlayerMessageBroker;
import org.me.newsky.redis.RedisCache;

import java.util.UUID;

public class PlayerMessageHandler {

    private final NewSky plugin;
    private final RedisCache redisCache;
    private PlayerMessageBroker playerMessageBroker;

    public PlayerMessageHandler(NewSky plugin, RedisCache redisCache) {
        this.plugin = plugin;
        this.redisCache = redisCache;
    }

    public void setPlayerMessageBroker(PlayerMessageBroker playerMessageBroker) {
        this.playerMessageBroker = playerMessageBroker;
    }

    public void sendPlayerMessage(UUID playerUuid, Component message) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            redisCache.getPlayerOnlineServer(playerUuid).ifPresent(playerServer -> {
                playerMessageBroker.sendPlayerMessage(playerServer, playerUuid, message);
            });
        });
    }
}