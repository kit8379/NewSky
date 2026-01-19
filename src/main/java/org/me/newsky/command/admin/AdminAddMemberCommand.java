package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerAlreadyExistsException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /isadmin addmember <member> <owner>
 */
public class AdminAddMemberCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminAddMemberCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "addmember";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminAddMemberAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminAddMemberPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminAddMemberSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminAddMemberDescription();
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

        Optional<UUID> islandOwnerUuidOpt = api.getPlayerUuid(islandOwnerName);
        if (islandOwnerUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(islandOwnerName));
            return true;
        }

        UUID targetMemberUuid = targetMemberUuidOpt.get();
        UUID islandOwnerUuid = islandOwnerUuidOpt.get();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(islandOwnerUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            return true;
        }

        api.addMember(islandUuid, targetMemberUuid, "member").thenRun(() -> {
            sender.sendMessage(config.getAdminAddMemberSuccessMessage(targetMemberName, islandOwnerName));
            api.sendPlayerMessage(targetMemberUuid, config.getWasAddedToIslandMessage(islandOwnerName));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandAlreadyExistException) {
                sender.sendMessage(config.getAlreadyHasIslandMessage(targetMemberName));
            } else if (cause instanceof IslandPlayerAlreadyExistsException) {
                sender.sendMessage(config.getIslandMemberExistsMessage(targetMemberName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error adding member " + targetMemberName + " to island of " + islandOwnerName, ex);
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
