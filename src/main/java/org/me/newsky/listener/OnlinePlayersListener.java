package org.me.newsky.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.me.newsky.NewSky;
import org.me.newsky.cluster.OnlinePlayerRegistry;
import org.me.newsky.thread.KeyedSequentialExecutor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OnlinePlayersListener implements Listener {

    private final NewSky plugin;
    private final OnlinePlayerRegistry onlinePlayerRegistry;
    private final String serverID;

    // Registry updates for one player must reach Redis in event order: a relog fires quit then
    // join, and two unordered async tasks could apply them reversed, deleting the fresh entry.
    private final KeyedSequentialExecutor<UUID> updateChains = new KeyedSequentialExecutor<>();

    public OnlinePlayersListener(NewSky plugin, OnlinePlayerRegistry onlinePlayerRegistry, String serverID) {
        this.plugin = plugin;
        this.onlinePlayerRegistry = onlinePlayerRegistry;
        this.serverID = serverID;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();

        enqueue(playerUuid, () -> {
            onlinePlayerRegistry.addOnlinePlayer(playerUuid, playerName, serverID);
            plugin.debug("OnlinePlayersListener", "Player " + playerName + " joined on server " + serverID);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();

        enqueue(playerUuid, () -> {
            onlinePlayerRegistry.removeOnlinePlayer(playerUuid, serverID);
            plugin.debug("OnlinePlayersListener", "Player " + playerName + " quit from server " + serverID);
        });
    }

    private void enqueue(UUID playerUuid, Runnable update) {
        updateChains.submit(playerUuid, () -> CompletableFuture.runAsync(update, plugin.getBukkitAsyncExecutor())).whenComplete((result, error) -> {
            if (error != null) {
                plugin.severe("Failed to update online player registry for " + playerUuid, error);
            }
        });
    }
}
