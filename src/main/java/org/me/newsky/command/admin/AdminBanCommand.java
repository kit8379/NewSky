package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.CannotBanIslandPlayerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerAlreadyBannedException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /isadmin ban <owner> <player>
 */
public class AdminBanCommand implements SubCommand, TabComplete {
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

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(islandOwnerUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            return true;
        }

        api.banPlayer(islandUuid, targetPlayerUuid).thenRun(() -> {
            sender.sendMessage(config.getAdminBanSuccessMessage(islandOwnerName, banPlayerName));
            api.sendPlayerMessage(targetPlayerUuid, config.getWasBannedFromIslandMessage(islandOwnerName));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerAlreadyBannedException) {
                sender.sendMessage(config.getPlayerAlreadyBannedMessage(banPlayerName));
            } else if (cause instanceof CannotBanIslandPlayerException) {
                sender.sendMessage(config.getPlayerCannotBanIslandPlayerMessage());
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error banning player " + banPlayerName + " from island of " + islandOwnerName, ex);
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
            String prefix = args[2].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
