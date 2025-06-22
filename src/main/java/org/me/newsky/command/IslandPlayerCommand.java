package org.me.newsky.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.player.*;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.exceptions.IslandDoesNotExistException;

import java.util.*;

/**
 * /is
 */
public class IslandPlayerCommand implements CommandExecutor, TabExecutor {
    private final ConfigHandler config;
    private final NewSkyAPI api;
    private final Map<String, SubCommand> subCommandMap = new HashMap<>();
    private final Set<SubCommand> subCommands = new HashSet<>();

    public IslandPlayerCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.config = config;
        this.api = api;
        subCommands.add(new PlayerCreateIslandCommand(plugin, api, config));
        subCommands.add(new PlayerDeleteIslandCommand(plugin, api, config));
        subCommands.add(new PlayerInviteCommand(plugin, api, config));
        subCommands.add(new PlayerAcceptInviteCommand(plugin, api, config));
        subCommands.add(new PlayerRejectInviteCommand(plugin, api, config));
        subCommands.add(new PlayerRemoveMemberCommand(plugin, api, config));
        subCommands.add(new PlayerHomeCommand(plugin, api, config));
        subCommands.add(new PlayerSetHomeCommand(plugin, api, config));
        subCommands.add(new PlayerDelHomeCommand(plugin, api, config));
        subCommands.add(new PlayerWarpCommand(plugin, api, config));
        subCommands.add(new PlayerSetWarpCommand(plugin, api, config));
        subCommands.add(new PlayerDelWarpCommand(plugin, api, config));
        subCommands.add(new PlayerSetOwnerCommand(plugin, api, config));
        subCommands.add(new PlayerLeaveCommand(plugin, api, config));
        subCommands.add(new PlayerLevelCommand(plugin, api, config));
        subCommands.add(new PlayerValueCommand(config));
        subCommands.add(new PlayerLockCommand(plugin, api, config));
        subCommands.add(new PlayerPvpCommand(plugin, api, config));
        subCommands.add(new PlayerTopCommand(plugin, api, config));
        subCommands.add(new PlayerInfoCommand(plugin, api, config));
        subCommands.add(new PlayerExpelCommand(plugin, api, config));
        subCommands.add(new PlayerBanCommand(plugin, api, config));
        subCommands.add(new PlayerUnbanCommand(plugin, api, config));
        subCommands.add(new PlayerBanListCommand(plugin, api, config));
        subCommands.add(new PlayerCoopCommand(plugin, api, config));
        subCommands.add(new PlayerUncoopCommand(plugin, api, config));
        subCommands.add(new PlayerCoopListCommand(plugin, api, config));
        subCommands.add(new PlayerLobbyCommand(plugin, api, config));
        subCommands.add(new PlayerHelpCommand(config, subCommands));

        for (SubCommand cmd : subCommands) {
            registerSubCommand(cmd);
        }
    }

    private void registerSubCommand(SubCommand cmd) {
        subCommandMap.put(cmd.getName().toLowerCase(), cmd);
        for (String alias : cmd.getAliases()) {
            subCommandMap.put(alias.toLowerCase(), cmd);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("newsky.island")) {
            sender.sendMessage(config.getNoPermissionCommandMessage());
            return true;
        }

        if (args.length == 0) {
            String mode = config.getBaseCommandMode();
            if ("island".equalsIgnoreCase(mode)) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
                    return true;
                }
                try {
                    api.getIslandUuid(player.getUniqueId());
                    // Has island → teleport home
                    SubCommand homeCmd = subCommandMap.get("home");
                    if (homeCmd != null) {
                        return homeCmd.execute(player, new String[]{"home"});
                    } else {
                        player.sendMessage(config.getPlayerNoHomeMessage("default"));
                        return true;
                    }
                } catch (IslandDoesNotExistException e) {
                    // No island → create island
                    SubCommand createCmd = subCommandMap.get("create");
                    if (createCmd != null) {
                        return createCmd.execute(player, new String[]{"create"});
                    } else {
                        player.sendMessage(config.getPlayerNoIslandMessage());
                        return true;
                    }
                }
            } else {
                // Default to help mode
                SubCommand helpCmd = subCommandMap.get("help");
                if (helpCmd != null) {
                    return helpCmd.execute(sender, new String[]{"help"});
                }
                return true;
            }
        }

        String subName = args[0].toLowerCase();
        SubCommand target = subCommandMap.get(subName);
        if (target == null) {
            sender.sendMessage(config.getUnknownSubCommandMessage());
            return true;
        }

        String perm = target.getPermission();
        if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
            sender.sendMessage(config.getNoPermissionCommandMessage());
            return true;
        }

        boolean success = target.execute(sender, args);
        if (!success) {
            sender.sendMessage(config.getCommandUsageMessage(target.getName(), target.getSyntax()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            for (SubCommand cmd : subCommands) {
                String perm = cmd.getPermission();
                if (perm == null || perm.isEmpty() || sender.hasPermission(perm)) {
                    if (cmd.getName().startsWith(args[0].toLowerCase())) {
                        suggestions.add(cmd.getName());
                    }
                    for (String alias : cmd.getAliases()) {
                        if (alias.startsWith(args[0].toLowerCase())) {
                            suggestions.add(alias);
                        }
                    }
                }
            }
            return suggestions;
        }

        SubCommand target = subCommandMap.get(args[0].toLowerCase());
        if (target instanceof TabComplete) {
            return ((TabComplete) target).tabComplete(sender, label, args);
        }
        return Collections.emptyList();
    }
}