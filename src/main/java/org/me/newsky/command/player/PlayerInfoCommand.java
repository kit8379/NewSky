package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.SubCommand;
import org.me.newsky.command.TabComplete;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /is info [player]
 */
public class PlayerInfoCommand implements SubCommand, TabComplete {
    private final NewSky plugin;
    private final NewSkyAPI api;
    private final ConfigHandler config;

    public PlayerInfoCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String[] getAliases() {
        return config.getPlayerInfoAliases();
    }

    @Override
    public String getPermission() {
        return config.getPlayerInfoPermission();
    }

    @Override
    public String getSyntax() {
        return config.getPlayerInfoSyntax();
    }

    @Override
    public String getDescription() {
        return config.getPlayerInfoDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        UUID targetUuid;

        if (args.length < 2) {
            targetUuid = player.getUniqueId();
        } else {
            Optional<UUID> targetOpt = api.getPlayerUuid(args[1]);
            if (targetOpt.isEmpty()) {
                sender.sendMessage(config.getUnknownPlayerMessage(args[1]));
                return true;
            }
            targetUuid = targetOpt.get();
        }

        try {
            UUID islandUuid = api.getIslandUuid(targetUuid);
            UUID ownerUuid = api.getIslandOwner(islandUuid);
            Set<UUID> members = api.getIslandMembers(islandUuid);
            int level = api.getIslandLevel(islandUuid);

            String ownerName = api.getPlayerName(ownerUuid).orElse(ownerUuid.toString());
            String memberNames = members.stream().map(uuid -> api.getPlayerName(uuid).orElse(uuid.toString())).collect(Collectors.joining(", "));

            sender.sendMessage(config.getIslandInfoHeaderMessage());
            sender.sendMessage(config.getIslandInfoUUIDMessage(islandUuid));
            sender.sendMessage(config.getIslandInfoOwnerMessage(ownerName));
            sender.sendMessage(config.getIslandInfoLevelMessage(level));
            if (memberNames.isEmpty()) {
                sender.sendMessage(config.getIslandInfoNoMembersMessage());
            } else {
                sender.sendMessage(config.getIslandInfoMembersMessage(memberNames));
            }

        } catch (IslandDoesNotExistException ex) {
            sender.sendMessage(config.getPlayerNoIslandMessage());
        } catch (Exception ex) {
            sender.sendMessage(config.getUnknownExceptionMessage());
            plugin.severe("Error getting island information for player " + player.getName(), ex);
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            return api.getOnlinePlayers().stream().filter(name -> name.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
