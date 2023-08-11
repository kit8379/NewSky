package org.me.newsky.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.me.newsky.NewSky;
import org.me.newsky.command.player.*;

import java.util.HashMap;
import java.util.Map;

public class IslandCommand implements CommandExecutor {
    private final NewSky plugin;
    private final Map<String, IslandSubCommand> subCommands;

    public IslandCommand(NewSky plugin) {
        this.plugin = plugin;
        subCommands = new HashMap<>();
        subCommands.put("create", new IslandCreateCommand(plugin));
        subCommands.put("delete", new IslandDeleteCommand(plugin));
        subCommands.put("add", new IslandAddMemberCommand(plugin));
        subCommands.put("remove", new IslandRemoveMemberCommand(plugin));
        subCommands.put("info", new IslandInfoCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            displayHelp(sender);
            return true;
        }

        IslandSubCommand subCommand = subCommands.get(args[0].toLowerCase());
        if (subCommand != null) {
            return subCommand.execute(sender, args);
        } else {
            displayHelp(sender);
            return true;
        }
    }

    public void displayHelp(CommandSender sender) {
        sender.sendMessage("Island Help");
        sender.sendMessage("/island create");
        sender.sendMessage("/island delete");
        sender.sendMessage("/island add <player>");
        sender.sendMessage("/island remove <player>");
        sender.sendMessage("/island info");
    }
}
