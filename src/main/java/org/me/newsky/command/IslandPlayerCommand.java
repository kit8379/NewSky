package org.me.newsky.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.player.PlayerHelpCommand;
import org.me.newsky.command.player.PlayerAddMemberCommand;
import org.me.newsky.command.player.PlayerBanCommand;
import org.me.newsky.command.player.PlayerBanListCommand;
import org.me.newsky.command.player.PlayerCoopCommand;
import org.me.newsky.command.player.PlayerCoopListCommand;
import org.me.newsky.command.player.PlayerCreateIslandCommand;
import org.me.newsky.command.player.PlayerDeleteIslandCommand;
import org.me.newsky.command.player.PlayerDelHomeCommand;
import org.me.newsky.command.player.PlayerDelWarpCommand;
import org.me.newsky.command.player.PlayerExpelCommand;
import org.me.newsky.command.player.PlayerHomeCommand;
import org.me.newsky.command.player.PlayerInfoCommand;
import org.me.newsky.command.player.PlayerLevelCommand;
import org.me.newsky.command.player.PlayerLeaveCommand;
import org.me.newsky.command.player.PlayerLockCommand;
import org.me.newsky.command.player.PlayerPvpCommand;
import org.me.newsky.command.player.PlayerRemoveMemberCommand;
import org.me.newsky.command.player.PlayerSetHomeCommand;
import org.me.newsky.command.player.PlayerSetOwnerCommand;
import org.me.newsky.command.player.PlayerSetWarpCommand;
import org.me.newsky.command.player.PlayerTopCommand;
import org.me.newsky.command.player.PlayerUnbanCommand;
import org.me.newsky.command.player.PlayerUncoopCommand;
import org.me.newsky.command.player.PlayerValueCommand;
import org.me.newsky.command.player.PlayerWarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.*;

/**
 * /is 主指令，負責將子指令委派給 player 目錄下的各類 SubCommand
 */
public class IslandPlayerCommand implements CommandExecutor, TabExecutor {
    private final ConfigHandler config;
    private final Map<String, SubCommand> subCommandMap = new HashMap<>();
    private final Set<SubCommand> subCommands = new HashSet<>();

    public IslandPlayerCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.config = config;
        subCommands.add(new PlayerCreateIslandCommand(plugin, api, config));
        subCommands.add(new PlayerDeleteIslandCommand(plugin, api, config));
        subCommands.add(new PlayerAddMemberCommand(plugin, api, config));
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
        // Check permission for the main command
        if (!sender.hasPermission("newsky.island")) {
            sender.sendMessage(config.getNoPermissionCommandMessage());
            return true;
        }

        // If no arguments are provided, show help command by default
        if (args.length == 0) {
            SubCommand helpCmd = subCommandMap.get("help");
            return helpCmd.execute(sender, new String[]{"help"});
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
