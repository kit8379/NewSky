package org.me.newsky.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.config.ConfigHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class BaseCommandExecutor implements CommandExecutor, TabCompleter {


    protected final ConfigHandler config;
    protected final Map<String, BaseCommand> commands;
    protected final List<String> subCommands;

    public BaseCommandExecutor(ConfigHandler config, Map<String, BaseCommand> commands) {
        this.config = config;
        this.commands = commands;
        this.subCommands = Arrays.asList(commands.keySet().toArray(new String[0]));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length == 0) {
            displayHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        BaseCommand command = commands.get(subCommand);

        if (command != null) {
            return command.execute(sender, args);
        } else {
            sender.sendMessage(getUnknownSubCommandMessage(subCommand));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return subCommands.stream().filter(sub -> sub.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        } else if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            BaseCommand commandObj = commands.get(subCommand);
            if (commandObj != null) {
                return commandObj.onTabComplete(sender, args);
            }
        }
        return null;
    }

    protected abstract void displayHelp(CommandSender sender);

    protected abstract String getUnknownSubCommandMessage(String subCommand);
}
