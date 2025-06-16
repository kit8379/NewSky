package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /isadmin setwarp <player> <warp>
 */
public class AdminSetWarpCommand implements SubCommand, TabComplete {
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
            Throwable cause = ex.getCause();
            if (cause instanceof IslandDoesNotExistException) {
                sender.sendMessage(config.getAdminNoIslandMessage(warpPlayerName));
            } else if (cause instanceof LocationNotInIslandException) {
                sender.sendMessage(config.getAdminMustInIslandSetWarpMessage(warpPlayerName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error setting warp " + warpName + " for " + warpPlayerName, ex);
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
            try {
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[1]);
                Set<String> warps = api.getWarpNames(targetPlayer.getUniqueId());
                String prefix = args[2].toLowerCase();
                return warps.stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }
}