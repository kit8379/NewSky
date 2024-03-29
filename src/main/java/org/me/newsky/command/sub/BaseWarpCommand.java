package org.me.newsky.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.exceptions.WarpDoesNotExistException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class BaseWarpCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final NewSkyAPI api;

    public BaseWarpCommand(ConfigHandler config, NewSkyAPI api) {
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

        String warpName = args.length > getTargetWarpArgIndex() ? args[getTargetWarpArgIndex()] : "default";
        api.warp(targetUuid, warpName).thenRun(() -> {
            sender.sendMessage(config.getWarpSuccessMessage(warpName));
        }).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(args[1]));
            } else if (ex.getCause() instanceof WarpDoesNotExistException) {
                sender.sendMessage(config.getNoWarpMessage(args[1], warpName));
            } else if (ex.getCause() instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage("There was an error teleporting to the home.");
                ex.printStackTrace();
            }
            return null;
        });

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == getTargetWarpArgIndex() + 1 && sender instanceof Player) {
            UUID targetUuid = ((Player) sender).getUniqueId();
            try {
                Set<String> warpNames = api.getWarpNames(targetUuid).get();
                return warpNames.stream().filter(name -> name.toLowerCase().startsWith(args[getTargetWarpArgIndex()].toLowerCase())).collect(Collectors.toList());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

}
