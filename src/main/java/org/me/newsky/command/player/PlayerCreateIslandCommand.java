package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.UUID;
import java.util.logging.Level;

public class PlayerCreateIslandCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerCreateIslandCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerCreateAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerCreatePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerCreateSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerCreateDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        api.createIsland(playerUuid).thenRun(() -> {
            player.sendMessage(config.getPlayerCreateSuccessMessage());
            api.home(playerUuid, "default", playerUuid).thenRun(() -> player.sendMessage(config.getPlayerHomeSuccessMessage("default")));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandAlreadyExistException) {
                player.sendMessage(config.getPlayerAlreadyHasIslandMessage());
            } else if (cause instanceof NoActiveServerException) {
                player.sendMessage(config.getNoActiveServerMessage());
            } else {
                player.sendMessage("There was an error creating the island");
                plugin.getLogger().log(Level.SEVERE, "Error creating island for player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }
}