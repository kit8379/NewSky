package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.AlreadyOwnerException;
import org.me.newsky.exceptions.IslandDoesNotExistException;
import org.me.newsky.exceptions.IslandPlayerDoesNotExistException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /is setowner <player>
 */
public class PlayerSetOwnerCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerSetOwnerCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "setowner";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerSetOwnerAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerSetOwnerPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerSetOwnerSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerSetOwnerDescription();
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

        api.setOwner(islandUuid, targetPlayerUuid).thenRun(() -> player.sendMessage(config.getPlayerSetOwnerSuccessMessage(targetPlayerName))).exceptionally(ex -> {
            Throwable cause = ex.getCause();
            if (cause instanceof IslandPlayerDoesNotExistException) {
                player.sendMessage(config.getIslandMemberNotExistsMessage(targetPlayerName));
            } else if (cause instanceof AlreadyOwnerException) {
                player.sendMessage(config.getPlayerAlreadyOwnerMessage(targetPlayerName));
            } else {
                player.sendMessage(config.getUnknownExceptionMessage());
                plugin.severe("Error setting owner for island for player " + player.getName(), ex);
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
                Set<UUID> members = api.getIslandMembers(islandUuid);
                String prefix = args[1].toLowerCase();
                return members.stream().map(uuid -> api.getPlayerName(uuid).orElse(uuid.toString())).filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }
}
