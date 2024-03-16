package org.me.newsky.command.base;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;

public abstract class BaseUnloadCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseUnloadCommand(ConfigHandler config, NewSkyAPI api) {
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

        // Unload the island
        api.islandAPI.unloadIsland(targetUuid).thenRun(() -> {
            sender.sendMessage(config.getIslandUnloadSuccessMessage(args[1]));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(args[1]));
            } else {
                sender.sendMessage("There was an error unloading the island");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    public abstract String getUsageCommandMessage();
}