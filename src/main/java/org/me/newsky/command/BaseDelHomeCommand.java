package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseDelHomeCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseDelHomeCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Get the target home name
        String homeName = args[getTargetHomeArgIndex()];

        // Check if the player target home point is default
        if (homeName.equals("default")) {
            sender.sendMessage(getCannotDeleteDefaultHomeMessage(args));
            return true;
        }

        // Check if the player has the target home point
        Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(targetUuid, homeName);
        if (homeLocationOpt.isEmpty()) {
            sender.sendMessage(getNoHomeMessage(args));
            return true;
        }

        // Delete the home point
        cacheHandler.deleteHomePoint(targetUuid, homeName);

        // Send the success message
        sender.sendMessage(getDelHomeSuccessMessage(args));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetHomeArgIndex();

    protected abstract String getCannotDeleteDefaultHomeMessage(String[] args);

    protected abstract String getNoHomeMessage(String[] args);

    protected abstract String getDelHomeSuccessMessage(String[] args);
}
