package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.PlayerNotBannedException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /isadmin unban <owner> <player>
 */
public class AdminUnbanCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public AdminUnbanCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "unban";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminUnbanAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminUnbanPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminUnbanSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminUnbanDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        String islandOwnerName = args[1];
        String banPlayerName = args[2];

        Optional<UUID> ownerUuidOpt = api.getPlayerUuid(islandOwnerName);
        if (ownerUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(islandOwnerName));
            return true;
        }
        UUID islandOwnerUuid = ownerUuidOpt.get();

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(banPlayerName);
        if (targetUuidOpt.isEmpty()) {
            sender.sendMessage(config.getUnknownPlayerMessage(banPlayerName));
            return true;
        }
        UUID targetPlayerUuid = targetUuidOpt.get();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(islandOwnerUuid);
        } catch (IslandDoesNotExistException e) {
            sender.sendMessage(config.getAdminNoIslandMessage(islandOwnerName));
            return true;
        }

        api.unbanPlayer(islandUuid, targetPlayerUuid).thenRun(() -> {
            sender.sendMessage(config.getAdminUnbanSuccessMessage(islandOwnerName, banPlayerName));
            api.sendPlayerMessage(targetPlayerUuid, config.getWasUnbannedFromIslandMessage(islandOwnerName));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerNotBannedException) {
                sender.sendMessage(config.getPlayerNotBannedMessage(banPlayerName));
            } else {
                sender.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error unbanning player " + banPlayerName + " from island of " + islandOwnerName, ex);
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
            Optional<UUID> ownerUuidOpt = api.getPlayerUuid(args[1]);
            if (ownerUuidOpt.isEmpty()) return Collections.emptyList();

            try {
                UUID islandUuid = api.getIslandUuid(ownerUuidOpt.get());
                Set<UUID> banned = api.getBannedPlayers(islandUuid);
                String prefix = args[2].toLowerCase();
                return banned.stream().map(uuid -> api.getPlayerName(uuid).orElse(uuid.toString())).filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }

        return Collections.emptyList();
    }
}
