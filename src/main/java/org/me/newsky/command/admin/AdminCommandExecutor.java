package org.me.newsky.command.admin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminCommandExecutor implements CommandExecutor, TabCompleter {

    private final ConfigHandler config;
    private final Map<String, CommandExecutor> commandMap;

    public AdminCommandExecutor(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.commandMap = new HashMap<>();
        commandMap.put("addmember", wrapCommand(new AdminAddMemberCommand(config, cacheHandler)));
        commandMap.put("removemember", wrapCommand(new AdminRemoveMemberCommand(config, cacheHandler)));
        commandMap.put("create", wrapCommand(new AdminCreateCommand(config, cacheHandler, islandHandler)));
        commandMap.put("delete", wrapCommand(new AdminDeleteCommand(config, cacheHandler, islandHandler)));
        commandMap.put("home", wrapCommand(new AdminHomeCommand(config, cacheHandler, islandHandler)));
        commandMap.put("sethome", wrapCommand(new AdminSetHomeCommand(config, cacheHandler)));
        commandMap.put("delhome", wrapCommand(new AdminDelHomeCommand(config, cacheHandler)));
        commandMap.put("warp", wrapCommand(new AdminWarpCommand(config, cacheHandler, islandHandler)));
        commandMap.put("setwarp", wrapCommand(new AdminSetWarpCommand(config, cacheHandler)));
        commandMap.put("delwarp", wrapCommand(new AdminDelWarpCommand(config, cacheHandler)));
        commandMap.put("info", wrapCommand(new AdminInfoCommand(config, cacheHandler)));
        commandMap.put("lock", wrapCommand(new AdminLockCommand(config, cacheHandler)));
        commandMap.put("pvp", wrapCommand(new AdminPvpCommand(config, cacheHandler)));
        commandMap.put("setowner", wrapCommand(new AdminSetOwnerCommand(config, cacheHandler)));
        commandMap.put("load", wrapCommand(new AdminLoadCommand(config, cacheHandler, islandHandler)));
        commandMap.put("unload", wrapCommand(new AdminUnloadCommand(config, cacheHandler, islandHandler)));
        commandMap.put("reload", wrapCommand(new AdminReloadCommand(plugin, config)));
    }

    private CommandExecutor wrapCommand(Object commandObject) {
        return (sender, command, label, args) -> {
            try {
                return (Boolean) commandObject.getClass().getMethod("execute", CommandSender.class, String[].class).invoke(commandObject, sender, args);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        };
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(config.getAdminCommandHelpMessage());
            return true;
        }

        String subCommand = args[0].toLowerCase();
        CommandExecutor commandExecutor = commandMap.get(subCommand);

        if (commandExecutor != null) {
            return commandExecutor.onCommand(sender, cmd, label, args);
        } else {
            sender.sendMessage(config.getAdminUnknownSubCommandMessage(subCommand));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return commandMap.keySet().stream().filter(sub -> sub.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            CommandExecutor commandExecutor = commandMap.get(subCommand);
            if (commandExecutor instanceof TabCompleter) {
                return ((TabCompleter) commandExecutor).onTabComplete(sender, command, alias, args);
            }
        }
        return null;
    }
}
