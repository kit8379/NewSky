package org.me.newsky.command.base;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class BaseDelHomeCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseDelHomeCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Get the target home name
        String homeName = args[getTargetHomeArgIndex()];

        // Check if the target player has an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Check if the player target home point is default
        if (homeName.equals("default")) {
            sender.sendMessage(getCannotDeleteDefaultHomeMessage(args));
            return true;
        }

        // Check if the player has the target home point
        Optional<String> homeLocationOpt = cacheHandler.getHomeLocation(islandUuid, targetUuid, homeName);
        if (homeLocationOpt.isEmpty()) {
            sender.sendMessage(getNoHomeMessage(args));
            return true;
        }

        // Delete the home point
        cacheHandler.deleteHomePoint(targetUuid, islandUuid, homeName);

        // Send the success message
        sender.sendMessage(getDelHomeSuccessMessage(args));

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == getTargetHomeArgIndex() + 1) {
            UUID targetUuid = getTargetUuid(sender, args);
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
            if (islandUuidOpt.isEmpty()) {
                return null;
            }
            UUID islandUuid = islandUuidOpt.get();
            Set<String> homeNames = cacheHandler.getHomeNames(islandUuid, targetUuid);
            return homeNames.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[getTargetHomeArgIndex()].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetHomeArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getNoHomeMessage(String[] args);

    protected abstract String getCannotDeleteDefaultHomeMessage(String[] args);

    protected abstract String getDelHomeSuccessMessage(String[] args);
}
