package org.me.newsky.command.base;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;

public abstract class BaseSetOwnerCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseSetOwnerCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the island owner's UUID
        UUID islandOwnerId = getIslandOwnerUuid(sender, args);

        // Get the target player's UUID
        OfflinePlayer targetOwner = Bukkit.getOfflinePlayer(args[getTargetOwnerArgIndex()]);
        UUID targetUuid = targetOwner.getUniqueId();

        // Set the new owner
        api.playerAPI.setOwner(islandOwnerId, targetUuid).thenRun(() -> {
            sender.sendMessage(getSetOwnerSuccessMessage(args));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(getNoIslandMessage(args));
            } else {
                sender.sendMessage("There was an error setting the owner");
                ex.printStackTrace();
            }
            return null;
        });
        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);

    protected abstract int getTargetOwnerArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getAlreadyOwnerMessage(String[] args);

    protected abstract String getSetOwnerSuccessMessage(String[] args);
}
