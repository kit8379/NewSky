// AdminSetWarpCommand.java
package org.me.newsky.command.admin;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.LocationNotInIslandException;

import java.util.*;
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

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(warpPlayerName);
        if (targetUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(warpPlayerName));
            return true;
        }
        UUID targetUuid = targetUuidOpt.get();

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        api.setWarp(targetUuid, warpName, worldName, x, y, z, yaw, pitch).thenRun(() -> sender.sendMessage(config.getAdminSetWarpSuccessMessage(warpPlayerName, warpName))).exceptionally(ex -> {
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
            return api.getOnlinePlayersNames().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        if (args.length == 3) {
            Optional<UUID> uuidOpt = api.getPlayerUuid(args[1]);
            if (uuidOpt.isEmpty()) return Collections.emptyList();

            Set<String> warps = api.getWarpNames(uuidOpt.get());
            String prefix = args[2].toLowerCase();
            return warps.stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
