package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /isadmin lock <player>
 */
public class AdminLockCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminLockCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "lock";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminLockAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminLockPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminLockSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminLockDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String targetPlayerName = args[1];

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(targetPlayerName);
        if (targetUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
            return true;
        }
        UUID targetUuid = targetUuidOpt.get();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(targetUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
            return true;
        }

        api.toggleIslandLock(islandUuid).thenAccept(isLocked -> {
            if (isLocked) {
                sender.sendMessage(config.getAdminLockSuccessMessage(targetPlayerName));
            } else {
                sender.sendMessage(config.getAdminUnLockSuccessMessage(targetPlayerName));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error toggling island lock for " + targetPlayerName, ex);
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}