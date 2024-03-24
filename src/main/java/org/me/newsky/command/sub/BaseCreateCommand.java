package org.me.newsky.command.sub;

import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;

import java.util.UUID;

public abstract class BaseCreateCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseCreateCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Create the island
        api.createIsland(targetUuid).thenRun(() -> {
            sender.sendMessage(getIslandCreateSuccessMessage(args));
            postCreateIsland(sender, args);
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandAlreadyExistException) {
                sender.sendMessage(getExistingIslandMessage(args));
            } else {
                sender.sendMessage("There was an error creating the island");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }


    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getExistingIslandMessage(String[] args);

    protected abstract String getIslandCreateSuccessMessage(String[] args);

    protected abstract void postCreateIsland(CommandSender sender, String[] args);
}
