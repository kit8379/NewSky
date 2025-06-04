package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin ban <owner> <player>
 */
public class AdminBanCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminBanCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminBanAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminBanPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminBanSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminBanDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String islandOwnerName = args[1];
        String banPlayerName = args[2];

        OfflinePlayer islandOwner = Bukkit.getOfflinePlayer(islandOwnerName);
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(banPlayerName);
        UUID islandOwnerUuid = islandOwner.getUniqueId();
        UUID targetPlayerUuid = targetPlayer.getUniqueId();

        api.getIslandUuid(islandOwnerUuid).thenCompose(islandUuid -> api.banPlayer(islandUuid, targetPlayerUuid)).thenRun(() -> sender.sendMessage(config.getAdminBanSuccessMessage(islandOwnerName, banPlayerName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            } else if (ex.getCause() instanceof PlayerAlreadyBannedException) {
                sender.sendMessage(config.getPlayerAlreadyBannedMessage(banPlayerName));
            } else if (ex.getCause() instanceof CannotBanIslandPlayerException) {
                sender.sendMessage(config.getPlayerCannotBanIslandPlayerMessage());
            } else {
                sender.sendMessage("There was an error banning the player.");
                plugin.getLogger().log(Level.SEVERE, "Error banning player " + banPlayerName + " from island of " + islandOwnerName, ex);
            }
            return null;
        });

        return true;
    }
}
