package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotRemoveOwnerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerDoesNotExistException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /isadmin removemember <member> <owner>
 */
public class AdminRemoveMemberCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminRemoveMemberCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "removemember";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminRemoveMemberAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminRemoveMemberPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminRemoveMemberSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminRemoveMemberDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String targetMemberName = args[1];
        String islandOwnerName = args[2];

        Optional<UUID> targetMemberUuidOpt = api.getPlayerUuid(targetMemberName);
        if (targetMemberUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(targetMemberName));
            return true;
        }
        UUID targetMemberUuid = targetMemberUuidOpt.get();

        Optional<UUID> islandOwnerUuidOpt = api.getPlayerUuid(islandOwnerName);
        if (islandOwnerUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(islandOwnerName));
            return true;
        }
        UUID islandOwnerUuid = islandOwnerUuidOpt.get();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(islandOwnerUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            return true;
        }

        api.removeMember(islandUuid, targetMemberUuid).thenRun(() -> {
            sender.sendMessage(config.getAdminRemoveMemberSuccessMessage(targetMemberName, islandOwnerName));
            api.sendPlayerMessage(targetMemberUuid, config.getWasRemovedFromIslandMessage(islandOwnerName));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof CannotRemoveOwnerException) {
                sender.sendMessage(config.getCannotRemoveOwnerMessage());
            } else if (cause instanceof IslandPlayerDoesNotExistException) {
                sender.sendMessage(config.getIslandMemberNotExistsMessage(targetMemberName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error removing member " + targetMemberName + " from island of " + islandOwnerName, ex);
            }
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

        if (args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
