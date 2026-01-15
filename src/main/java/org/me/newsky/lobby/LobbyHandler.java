package org.me.newsky.lobby;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.network.distributor.Distributor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LobbyHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final Distributor distributor;

    public LobbyHandler(NewSky plugin, ConfigHandler config, Distributor distributor) {
        this.plugin = plugin;
        this.config = config;
        this.distributor = distributor;
    }

    public CompletableFuture<Void> lobby(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> lobbyServers = config.getLobbyServerNames();
            String lobbyWorld = config.getLobbyWorldName();
            String lobbyLocation = config.getLobbyX() + "," + config.getLobbyY() + "," + config.getLobbyZ() + "," + config.getLobbyYaw() + "," + config.getLobbyPitch();

            return distributor.teleportLobby(playerUuid, lobbyServers, lobbyWorld, lobbyLocation);
        }, plugin.getBukkitAsyncExecutor()).thenCompose(f -> f);
    }
}
