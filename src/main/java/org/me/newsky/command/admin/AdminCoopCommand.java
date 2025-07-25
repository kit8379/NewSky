package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /isadmin coop <owner> <player>
 */
public class AdminCoopCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminCoopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "coop";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminCoopAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminCoopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminCoopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminCoopDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String ownerName = args[1];
        String targetName = args[2];

        Optional<UUID> ownerUuidOpt = api.getPlayerUuid(ownerName);
        if (ownerUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(ownerName));
            return true;
        }

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(targetName);
        if (targetUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(targetName));
            return true;
        }

        UUID ownerUuid = ownerUuidOpt.get();
        UUID targetUuid = targetUuidOpt.get();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(ownerUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(ownerName));
            return true;
        }

        api.addCoop(islandUuid, targetUuid).thenRun(() -> {
            sender.sendMessage(config.getAdminCoopSuccessMessage(ownerName, targetName));
            api.sendMessage(targetUuid, config.getWasCoopedToIslandMessage(ownerName));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerAlreadyCoopedException) {
                sender.sendMessage(config.getPlayerAlreadyCoopedMessage(targetName));
            } else if (cause instanceof CannotCoopIslandPlayerException) {
                sender.sendMessage(config.getPlayerCannotCoopIslandPlayerMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error cooping player " + targetName + " to island of " + ownerName, ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return api.getOnlinePlayersNames().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 3) {
            String prefix = args[2].toLowerCase();
            return api.getOnlinePlayersNames().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
