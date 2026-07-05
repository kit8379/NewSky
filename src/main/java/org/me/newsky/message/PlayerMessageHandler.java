package org.me.newsky.message;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.JSONObject;
import org.me.newsky.NewSky;
import org.me.newsky.messaging.CrossServerMessenger;
import org.me.newsky.state.OnlinePlayerState;
import org.me.newsky.util.ComponentUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerMessageHandler {

    public static final String ACTION_PLAYER_MESSAGE_SEND = "player.message.send";

    private final NewSky plugin;
    private final CrossServerMessenger messenger;
    private final OnlinePlayerState onlinePlayerState;
    private final String serverID;

    public PlayerMessageHandler(NewSky plugin, CrossServerMessenger messenger, OnlinePlayerState onlinePlayerState, String serverID) {
        this.plugin = plugin;
        this.messenger = messenger;
        this.onlinePlayerState = onlinePlayerState;
        this.serverID = serverID;

        messenger.register(ACTION_PLAYER_MESSAGE_SEND, payload -> deliverLocal(UUID.fromString(payload.getString("uuid")), ComponentUtils.deserialize(payload.getString("component"))).thenApply(v -> new JSONObject()));
    }

    public void sendPlayerMessage(UUID playerUuid, Component message) {
        CompletableFuture.runAsync(() -> {
            String targetServer = onlinePlayerState.getOnlinePlayerServer(playerUuid);
            if (serverID.equals(targetServer)|| targetServer == null) {
                deliverLocal(playerUuid, message);
                return;
            }

            JSONObject payload = new JSONObject();
            payload.put("uuid", playerUuid.toString());
            payload.put("component", ComponentUtils.serialize(message));

            messenger.requestVoid(targetServer, ACTION_PLAYER_MESSAGE_SEND, payload).exceptionally(e -> {
                plugin.severe("Failed to send cross-server player message to " + playerUuid, e);
                return null;
            });
        }, plugin.getBukkitAsyncExecutor());
    }

    private CompletableFuture<Void> deliverLocal(UUID playerUuid, Component message) {
        return CompletableFuture.runAsync(() -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(message);
                plugin.debug("PlayerMessageHandler", "Delivered message to player " + playerUuid);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin));
    }
}
