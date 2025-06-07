package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerNotCoopedException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin uncoop <owner> <player>
 */
public class AdminUncoopCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminUncoopCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "uncoop";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminUncoopAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminUncoopPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminUncoopSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminUncoopDescription();
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

        api.removeCoop(islandUuid, targetUuid).thenRun(() -> sender.sendMessage(config.getAdminUncoopSuccessMessage(ownerName, targetName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerNotCoopedException) {
                sender.sendMessage(config.getPlayerNotCoopedMessage(targetName));
            } else {
                sender.sendMessage("There was an error uncooping the player.");
                plugin.getLogger().log(Level.SEVERE, "Error uncooping player " + targetName + " from island of " + ownerName, ex);
            }
            return null;
        });

        return true;
    }
}
