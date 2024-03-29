package org.me.newsky.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyLoadedException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;

import java.util.UUID;

public abstract class BaseLoadCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseLoadCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the command arguments are valid
        if (args.length < 2) {
            sender.sendMessage(config.getUsagePrefix() + getUsageCommandMessage());
            return false;
        }

        // Get the target player's UUID
        UUID targetUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        // Load the island
        api.loadIsland(targetUuid).thenRun(() -> {
            sender.sendMessage(config.getIslandLoadSuccessMessage(args[1]));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(args[1]));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else if (ex.getCause() instanceof IslandAlreadyLoadedException) {
                sender.sendMessage(config.getIslandAlreadyLoadedMessage());
            } else {
                sender.sendMessage("There was an error loading the island");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    public abstract String getUsageCommandMessage();
}
