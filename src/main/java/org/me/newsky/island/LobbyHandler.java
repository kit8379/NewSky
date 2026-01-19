package org.me.newsky.island;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.network.distributor.IslandDistributor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LobbyHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final IslandDistributor islandDistributor;

    public LobbyHandler(NewSky plugin, ConfigHandler config, IslandDistributor islandDistributor) {
        this.plugin = plugin;
        this.config = config;
        this.islandDistributor = islandDistributor;
    }

    public CompletableFuture<Void> lobby(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> lobbyServers = config.getLobbyServerNames();
            String lobbyWorld = config.getLobbyWorldName();
            String lobbyLocation = config.getLobbyX() + "," + config.getLobbyY() + "," + config.getLobbyZ() + "," + config.getLobbyYaw() + "," + config.getLobbyPitch();

            return islandDistributor.teleportLobby(playerUuid, lobbyServers, lobbyWorld, lobbyLocation);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(f -> f);
    }
}
