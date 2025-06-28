package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /isadmin warp <player> [warp] [target]
 */
public class AdminWarpCommand implements SubCommand, TabComplete {
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

        Optional<UUID> warpPlayerUuidOpt = api.getPlayerUuid(warpPlayerName);
        if (warpPlayerUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(warpPlayerName));
            return true;
        }
        UUID warpPlayerUuid = warpPlayerUuidOpt.get();

        UUID senderUuid;
        if (teleportPlayerName == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
                return true;
            }
            senderUuid = player.getUniqueId();
        } else {
            Optional<UUID> targetUuidOpt = api.getPlayerUuid(teleportPlayerName);
            if (targetUuidOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(teleportPlayerName));
                return true;
            }
            senderUuid = targetUuidOpt.get();
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
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error teleporting to warp " + warpName + " of " + warpPlayerName, ex);
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
            Optional<UUID> ownerUuidOpt = api.getPlayerUuid(args[1]);
            if (ownerUuidOpt.isEmpty()) return Collections.emptyList();
            try {
                Set<String> warps = api.getWarpNames(ownerUuidOpt.get());
                String prefix = args[2].toLowerCase();
                return warps.stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

        if (args.length == 4) {
            String prefix = args[3].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}