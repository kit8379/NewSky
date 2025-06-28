package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
 * /is unban <player>
 */
public class PlayerUnbanCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerUnbanCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
        return config.getPlayerUnbanAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerUnbanPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerUnbanSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerUnbanDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        if (args.length < 2) {
            return false;
        }

        String targetPlayerName = args[1];
        UUID playerUuid = player.getUniqueId();

        Optional<UUID> targetUuidOpt = api.getPlayerUuid(targetPlayerName);
        if (targetUuidOpt.isEmpty()) {
            player.sendMessage(config.getUnknownPlayerMessage(targetPlayerName));
            return true;
        }
        UUID targetPlayerUuid = targetUuidOpt.get();

        UUID islandUuid;
        try {
            islandUuid = api.getIslandUuid(playerUuid);
        } catch (IslandDoesNotExistException e) {
            player.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }

        api.unbanPlayer(islandUuid, targetPlayerUuid).thenRun(() -> {
            player.sendMessage(config.getPlayerUnbanSuccessMessage(targetPlayerName));
            api.sendMessage(targetPlayerUuid, config.getWasUnbannedFromIslandMessage(player.getName()));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof PlayerNotBannedException) {
                player.sendMessage(config.getPlayerNotBannedMessage(targetPlayerName));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error unbanning player " + targetPlayerName + " from island of player " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2 && sender instanceof Player player) {
            try {
                UUID islandUuid = api.getIslandUuid(player.getUniqueId());
                Set<UUID> banned = api.getBannedPlayers(islandUuid);
                String prefix = args[1].toLowerCase();
                return banned.stream().map(uuid -> api.getPlayerName(uuid).orElse(uuid.toString())).filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}
