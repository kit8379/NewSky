package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.UUID;

public class PlayerLobbyCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerLobbyCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "lobby";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerLobbyAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerLobbyPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerLobbySyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerLobbyDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        api.lobby(playerUuid).thenRun(() -> {
            api.sendPlayerMessage(playerUuid, config.getPlayerLobbySuccessMessage());
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error teleporting player " + player.getName() + " to lobby", ex);
            }
            return null;
        });

        return true;
    }
}
