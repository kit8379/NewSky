package org.me.newsky.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.admin.*;
import org.me.newsky.config.ConfigHandler;

import java.util.*;

/**
 * /isadmin
 */
public class IslandAdminCommand implements CommandExecutor, TabExecutor {
    private final ConfigHandler config;
    private final Map<String, SubCommand> subCommandMap = new HashMap<>();
    private final Set<SubCommand> subCommands = new HashSet<>();

    public IslandAdminCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
        this.config = config;
        subCommands.add(new AdminReloadCommand(plugin, config));
        subCommands.add(new AdminCreateIslandCommand(plugin, api, config));
        subCommands.add(new AdminDeleteIslandCommand(plugin, api, config));
        subCommands.add(new AdminAddMemberCommand(plugin, api, config));
        subCommands.add(new AdminRemoveMemberCommand(plugin, api, config));
        subCommands.add(new AdminHomeCommand(plugin, api, config));
        subCommands.add(new AdminSetHomeCommand(plugin, api, config));
        subCommands.add(new AdminDelHomeCommand(plugin, api, config));
        subCommands.add(new AdminWarpCommand(plugin, api, config));
        subCommands.add(new AdminSetWarpCommand(plugin, api, config));
        subCommands.add(new AdminDelWarpCommand(plugin, api, config));
        subCommands.add(new AdminLockCommand(plugin, api, config));
        subCommands.add(new AdminPvpCommand(plugin, api, config));
        subCommands.add(new AdminLoadCommand(plugin, api, config));
        subCommands.add(new AdminUnloadCommand(plugin, api, config));
        subCommands.add(new AdminBanCommand(plugin, api, config));
        subCommands.add(new AdminUnbanCommand(plugin, api, config));
        subCommands.add(new AdminCoopCommand(plugin, api, config));
        subCommands.add(new AdminUncoopCommand(plugin, api, config));
        subCommands.add(new AdminLobbyCommand(plugin, api, config));
        subCommands.add(new AdminHelpCommand(config, subCommands));

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
        if (!sender.hasPermission("newsky.island.admin")) {
            sender.sendMessage(config.getNoPermissionCommandMessage());
            return true;
        }

        // If no arguments are provided, show help
        if (args.length == 0) {
            SubCommand helpCmd = subCommandMap.get("help");
            return helpCmd.execute(sender, new String[]{"help"});
        }

        String subName = args[0].toLowerCase();
        SubCommand target = subCommandMap.get(subName);
        if (target == null) {
            sender.sendMessage(config.getAdminUnknownSubCommandMessage());
            return true;
        }

        String perm = target.getPermission();
        if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
            sender.sendMessage(config.getNoPermissionCommandMessage());
            return true;
        }

        boolean success = target.execute(sender, args);
        if (!success) {
            sender.sendMessage(config.getAdminCommandUsageMessage(target.getName(), target.getSyntax()));
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