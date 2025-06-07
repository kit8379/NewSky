package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerDoesNotExistException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin removemember <member> <owner>
 */
public class AdminRemoveMemberCommand implements SubCommand {
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

        api.removeMember(islandUuid, targetMemberUuid).thenRun(() -> sender.sendMessage(config.getAdminRemoveMemberSuccessMessage(targetMemberName, islandOwnerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandPlayerDoesNotExistException) {
                sender.sendMessage(config.getIslandMemberNotExistsMessage(targetMemberName));
            } else {
                sender.sendMessage("There was an error removing the member");
                plugin.getLogger().log(Level.SEVERE, "Error removing member " + targetMemberName + " from island of " + islandOwnerName, ex);
            }
            return null;
        });

        return true;
    }
}