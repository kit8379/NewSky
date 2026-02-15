package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.WarpDoesNotExistException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /isadmin delwarp <player> <warp>
 */
public class AdminDelWarpCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminDelWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delwarp";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminDelWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminDelWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminDelWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminDelWarpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String warpPlayerName = args[1];
        String warpName = args[2];

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(warpPlayerName);
        if (targetUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(warpPlayerName));
            return true;
        }
        UUID targetUuid = targetUuidOpt.get();

        api.delWarp(targetUuid, warpName).thenRun(() ->
                sender.sendMessage(config.getAdminDelWarpSuccessMessage(warpPlayerName, warpName))
        ).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(warpPlayerName));
            } else if (cause instanceof WarpDoesNotExistException) {
                sender.sendMessage(config.getNoWarpMessage(warpPlayerName, warpName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error deleting warp " + warpName + " for " + warpPlayerName, ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            Optional<UUID> uuidOpt = api.getPlayerUuid(args[1]);
            if (uuidOpt.isPresent()) {
                return api.getWarpNames(uuidOpt.get()).stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
