package org.me.newsky.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;

public abstract class BaseLockCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseLockCommand(ConfigHandler config, NewSkyAPI api) {
        this.config = config;
        this.api = api;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Cast the sender to a player
        Player player = (Player) sender;

        // Get the target UUID
        UUID targetUuid = player.getUniqueId();

        // Toggle the lock status of the island
        api.toggleIslandLock(targetUuid).thenAccept(isLocked -> {
            if (isLocked) {
                sender.sendMessage(getIslandLockSuccessMessage(args));
            } else {
                sender.sendMessage(getIslandUnLockSuccessMessage(args));
            }
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(getNoIslandMessage(args));
            } else {
                sender.sendMessage("There was an error toggling the island lock status");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandUnLockSuccessMessage(String[] args);

    protected abstract String getIslandLockSuccessMessage(String[] args);
}
