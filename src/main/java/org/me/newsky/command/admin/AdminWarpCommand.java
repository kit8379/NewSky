package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandLockedException;
import org.me.newsky.exceptions.NoActiveServerException;
import org.me.newsky.exceptions.PlayerBannedException;
import org.me.newsky.exceptions.WarpDoesNotExistException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin warp <player> [warp] [target]
 */
public class AdminWarpCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "warp";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminWarpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        String warpPlayerName = args[1];
        String warpName = (args.length >= 3) ? args[2] : "default";
        String teleportPlayerName = (args.length >= 4) ? args[3] : null;

        OfflinePlayer warpPlayer = Bukkit.getOfflinePlayer(warpPlayerName);
        UUID warpPlayerUuid = warpPlayer.getUniqueId();
        UUID senderUuid;

        if (teleportPlayerName == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
                return true;
            }
            senderUuid = player.getUniqueId();
        } else {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(teleportPlayerName);
            senderUuid = targetPlayer.getUniqueId();
        }

        api.warp(warpPlayerUuid, warpName, senderUuid).thenRun(() -> sender.sendMessage(config.getWarpSuccessMessage(warpName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getNoIslandMessage(warpPlayerName));
            } else if (cause instanceof WarpDoesNotExistException) {
                sender.sendMessage(config.getNoWarpMessage(warpPlayerName, warpName));
            } else if (cause instanceof PlayerBannedException) {
                sender.sendMessage(config.getPlayerBannedMessage());
            } else if (cause instanceof IslandLockedException) {
                sender.sendMessage(config.getIslandLockedMessage());
            } else if (cause instanceof NoActiveServerException) {
                sender.sendMessage(config.getNoActiveServerMessage());
            } else {
                sender.sendMessage("There was an error teleporting to the warp.");
                plugin.getLogger().log(Level.SEVERE, "Error teleporting to warp " + warpName + " of " + warpPlayerName, ex);
            }
            return null;
        });

        return true;
    }
}