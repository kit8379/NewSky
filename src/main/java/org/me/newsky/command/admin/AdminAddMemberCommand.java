package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import java.util.UUID;
import java.util.logging.Level;
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

        OfflinePlayer targetMember = Bukkit.getOfflinePlayer(targetMemberName);
        OfflinePlayer islandOwner = Bukkit.getOfflinePlayer(islandOwnerName);
        UUID targetMemberUuid = targetMember.getUniqueId();
        UUID islandOwnerUuid = islandOwner.getUniqueId();

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
                plugin.getLogger().log(Level.SEVERE, "Error adding member " + targetMemberName + " to island of " + islandOwnerName, ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 3) {
            String prefix = args[2].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
