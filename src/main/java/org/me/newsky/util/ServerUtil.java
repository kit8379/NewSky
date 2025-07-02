package org.me.newsky.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ServerUtil {

    /**
     * Connects a player to a specified server using BungeeCord plugin messaging.
     *
     * @param plugin     the NewSky plugin instance
     * @param playerUuid the UUID of the player to connect
     * @param serverName the name of the server to connect to
     * @return a CompletableFuture that completes when the connection message is sent
     */
    public static CompletableFuture<Void> connectToServer(NewSky plugin, UUID playerUuid, String serverName) {
        return CompletableFuture.runAsync(() -> {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null) {
                try (ByteArrayOutputStream byteArray = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(byteArray)) {
                    out.writeUTF("Connect");
                    out.writeUTF(serverName);
                    player.sendPluginMessage(plugin, "BungeeCord", byteArray.toByteArray());
                } catch (IOException e) {
                    plugin.severe("Failed to send plugin message to player " + player.getName(), e);
                    throw new RuntimeException(e);
                }
            } else {
                throw new IllegalStateException("Player not found: " + playerUuid);
            }
        }, Bukkit.getScheduler().getMainThreadExecutor(plugin)).thenRunAsync(() -> plugin.debug("ServerUtil", "Sent connect message to player " + playerUuid + " for server: " + serverName), plugin.getBukkitAsyncExecutor());
    }
}