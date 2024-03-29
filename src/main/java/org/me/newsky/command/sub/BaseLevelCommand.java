package org.me.newsky.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;

public abstract class BaseLevelCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseLevelCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        // Validate the command arguments
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Calculate the island level
        api.getIslandLevel(targetUuid).thenAccept(level -> {
            sender.sendMessage(getIslandLevelSuccessMessage(level));
            api.calculateIslandLevel(targetUuid);
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(getNoIslandMessage(args));
            } else {
                sender.sendMessage("There was an error calculating the island level.");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandLevelSuccessMessage(int level);
}
