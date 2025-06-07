package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotCoopIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyCoopedException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin coop <owner> <player>
 */
public class AdminCoopCommand implements SubCommand {
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

        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerName);
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID ownerUuid = owner.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(ownerUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(ownerName));
            return true;
        }

        api.addCoop(islandUuid, targetUuid).thenRun(() -> sender.sendMessage(config.getAdminCoopSuccessMessage(ownerName, targetName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerAlreadyCoopedException) {
                sender.sendMessage(config.getPlayerAlreadyCoopedMessage(targetName));
            } else if (cause instanceof CannotCoopIslandPlayerException) {
                sender.sendMessage(config.getPlayerCannotCoopIslandPlayerMessage());
            } else {
                sender.sendMessage("There was an error cooping the player.");
                plugin.getLogger().log(Level.SEVERE, "Error cooping player " + targetName + " to island of " + ownerName, ex);
            }
            return null;
        });

        return true;
    }
}
