package org.me.newsky.command.player;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.*;
import java.util.stream.Collectors;

public class IslandCommandExecutor implements CommandExecutor, TabCompleter {

    private final ConfigHandler config;
    private final Map<String, CommandExecutor> commandMap;

    public IslandCommandExecutor(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.commandMap = new HashMap<>();
        commandMap.put("addmember", wrapCommand(new IslandAddMemberCommand(config, cacheHandler)));
        commandMap.put("removemember", wrapCommand(new IslandRemoveMemberCommand(config, cacheHandler)));
        commandMap.put("create", wrapCommand(new IslandCreateCommand(config, cacheHandler, islandHandler)));
        commandMap.put("delete", wrapCommand(new IslandDeleteCommand(config, cacheHandler, islandHandler)));
        commandMap.put("home", wrapCommand(new IslandHomeCommand(config, cacheHandler, islandHandler)));
        commandMap.put("sethome", wrapCommand(new IslandSetHomeCommand(config, cacheHandler)));
        commandMap.put("delhome", wrapCommand(new IslandDelHomeCommand(config, cacheHandler)));
        commandMap.put("warp", wrapCommand(new IslandWarpCommand(config, cacheHandler, islandHandler)));
        commandMap.put("setwarp", wrapCommand(new IslandSetWarpCommand(config, cacheHandler)));
        commandMap.put("delwarp", wrapCommand(new IslandDelWarpCommand(config, cacheHandler)));
        commandMap.put("info", wrapCommand(new IslandInfoCommand(config, cacheHandler)));
        commandMap.put("lock", wrapCommand(new IslandLockCommand(config, cacheHandler)));
        commandMap.put("pvp", wrapCommand(new IslandPvpCommand(config, cacheHandler)));
        commandMap.put("setowner", wrapCommand(new IslandSetOwnerCommand(config, cacheHandler)));
        commandMap.put("leave", wrapCommand(new IslandLeaveCommand(config, cacheHandler)));
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
            sender.sendMessage(config.getPlayerCommandHelpMessage());
            return true;
        }

        String subCommand = args[0].toLowerCase();
        CommandExecutor commandExecutor = commandMap.get(subCommand);

        if (commandExecutor != null) {
            return commandExecutor.onCommand(sender, cmd, label, args);
        } else {
            sender.sendMessage(config.getPlayerUnknownSubCommandMessage(subCommand));
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
