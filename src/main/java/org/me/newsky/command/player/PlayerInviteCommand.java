package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.InvitedAlreadyException;
import org.me.newsky.exceptions.IslandAlreadyExistException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerAlreadyExistsException;
import org.me.newsky.island.UpgradeHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /is invite <player>
 */
public class PlayerInviteCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerInviteCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "invite";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerInviteAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerInvitePermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerInviteSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerInviteDescription();
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

        if (!api.getOnlinePlayersNames().contains(targetPlayerName)) {
            player.sendMessage(config.getPlayerNotOnlineMessage(targetPlayerName));
            return true;
        }

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

        int teamLimitLevel = api.getCurrentUpgradeLevel(islandUuid, UpgradeHandler.UPGRADE_TEAM_LIMIT);
        int teamLimit = api.getTeamLimit(teamLimitLevel);
        if (api.getIslandMembers(islandUuid).size() >= teamLimit) {
            player.sendMessage(config.getPlayerTeamLimitReachedMessage(teamLimit));
            return true;
        }

        api.addPendingInvite(targetPlayerUuid, islandUuid, playerUuid, 600).thenRun(() -> {
            player.sendMessage(config.getPlayerInviteSentMessage(targetPlayerName));
            api.sendPlayerMessage(targetPlayerUuid, config.getPlayerInviteReceiveMessage(player.getName()));
        }).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof InvitedAlreadyException) {
                player.sendMessage(config.getPlayerAlreadyInvitedMessage(targetPlayerName));
            } else if (cause instanceof IslandAlreadyExistException) {
                player.sendMessage(config.getAlreadyHasIslandMessage(targetPlayerName));
            } else if (cause instanceof IslandPlayerAlreadyExistsException) {
                player.sendMessage(config.getIslandMemberExistsMessage(targetPlayerName));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error inviting player " + targetPlayerName + " to island of " + player.getName(), ex);
            }
            return null;
        });

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return api.getOnlinePlayersNames().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}