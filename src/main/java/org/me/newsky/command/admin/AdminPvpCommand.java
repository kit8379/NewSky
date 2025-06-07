package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin pvp <player>
 */
public class AdminPvpCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminPvpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "pvp";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminPvpAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminPvpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminPvpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminPvpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String targetPlayerName = args[1];

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(targetUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(targetPlayerName));
            return true;
        }

        api.toggleIslandPvp(islandUuid).thenAccept(isPvpEnabled -> {
            if (isPvpEnabled) {
                sender.sendMessage(config.getAdminPvpEnableSuccessMessage(targetPlayerName));
            } else {
                sender.sendMessage(config.getAdminPvpDisableSuccessMessage(targetPlayerName));
            }
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error toggling the PvP status.");
            plugin.getLogger().log(Level.SEVERE, "Error toggling PvP status for " + targetPlayerName, ex);
            return null;
        });

        return true;
    }
}