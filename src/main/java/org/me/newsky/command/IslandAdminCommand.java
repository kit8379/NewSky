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
 * /isadmin 主指令，負責將子指令委派給 admin 目錄下的各類 SubCommand
 */
public class IslandAdminCommand implements CommandExecutor, TabExecutor {
    private final Map<String, SubCommand> subCommandMap = new HashMap<>();
    private final Set<SubCommand> subCommands = new HashSet<>();

    public IslandAdminCommand(NewSky plugin, NewSkyAPI api, ConfigHandler config) {
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // If no arguments are provided, show help
        if (args.length == 0) {
            SubCommand helpCmd = subCommandMap.get("help");
            return helpCmd.execute(sender, new String[]{"help"});
        }

        String subName = args[0].toLowerCase();
        SubCommand target = subCommandMap.get(subName);
        if (target == null) {
            sender.sendMessage("§c子指令不存在，輸入 /isadmin 或 /isadmin help 以查看所有可用指令。");
            return true;
        }

        String perm = target.getPermission();
        if (perm != null && !perm.isEmpty() && !sender.hasPermission(perm)) {
            sender.sendMessage("§c你沒有權限使用此指令。");
            return true;
        }

        boolean success = target.execute(sender, args);
        if (!success) {
            sender.sendMessage("§c使用方式：/isadmin " + target.getName() + " " + target.getSyntax());
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