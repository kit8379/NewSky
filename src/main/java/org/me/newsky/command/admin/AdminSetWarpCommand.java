package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;

import java.util.UUID;
import java.util.logging.Level;

/**
 * /isadmin setwarp <player> <warp>
 */
public class AdminSetWarpCommand implements SubCommand {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminSetWarpCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "setwarp";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminSetWarpAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminSetWarpPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminSetWarpSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminSetWarpDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        String warpPlayerName = args[1];
        String warpName = args[2];

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(warpPlayerName);
        UUID targetUuid = targetPlayer.getUniqueId();
        Location loc = player.getLocation();

        api.setWarp(targetUuid, warpName, loc).thenRun(() -> sender.sendMessage(config.getAdminSetWarpSuccessMessage(warpPlayerName, warpName))).exceptionally(ex -> {
            if (ex.getCause() instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(warpPlayerName));
            } else if (ex.getCause() instanceof LocationNotInIslandException) {
                sender.sendMessage(config.getAdminMustInIslandSetWarpMessage(warpPlayerName));
            } else {
                sender.sendMessage("There was an error setting the warp.");
                plugin.getLogger().log(Level.SEVERE, "Error setting warp " + warpName + " for " + warpPlayerName, ex);
            }
            return null;
        });

        return true;
    }
}
