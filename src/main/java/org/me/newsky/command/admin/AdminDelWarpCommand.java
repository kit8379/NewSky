package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.WarpDoesNotExistException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin delwarp <player> <warp>
 */
public class AdminDelWarpCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminDelWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "delwarp";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminDelWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminDelWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminDelWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminDelWarpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String warpPlayerName = args[1];
        String warpName = args[2];

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(warpPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();

        api.delWarp(targetUuid, warpName).thenRun(() -> sender.sendMessage(config.getAdminDelWarpSuccessMessage(warpPlayerName, warpName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(warpPlayerName));
            } else if (cause instanceof WarpDoesNotExistException) {
                sender.sendMessage(config.getAdminNoWarpMessage(warpPlayerName, warpName));
            } else {
                sender.sendMessage("There was an error deleting the warp.");
                plugin.getLogger().log(Level.SEVERE, "Error deleting warp " + warpName + " for " + warpPlayerName, ex);
            }
            return null;
        });

        return true;
    }
}
