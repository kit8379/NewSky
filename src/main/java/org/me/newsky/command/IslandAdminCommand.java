package org.me.newsky.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.command.admin.*;

import java.util.HashMap;
import java.util.Map;

public class IslandAdminCommand implements CommandExecutor {
    private final NewSky plugin;
    private final Map<String, IslandSubCommand> subCommands;

    public IslandAdminCommand(NewSky plugin) {
        this.plugin = plugin;
        subCommands = new HashMap<>();
        subCommands.put("create", new AdminCreateCommand(plugin));
        subCommands.put("delete", new AdminDeleteCommand(plugin));
        subCommands.put("add", new AdminAddMemberCommand(plugin));
        subCommands.put("remove", new AdminRemoveMemberCommand(plugin));
        subCommands.put("info", new AdminInfoCommand(plugin));
        subCommands.put("listworld", new AdminListWorldCommand(plugin));
        subCommands.put("reload", new AdminReloadCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
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
        Component help = Component.text("Island Admin Help", NamedTextColor.GOLD);
        help = help.append(Component.text("\n/islandadmin create <player>", NamedTextColor.GOLD));
        help = help.append(Component.text("\n/islandadmin delete <player>", NamedTextColor.GOLD));
        help = help.append(Component.text("\n/islandadmin add <player> <island owner>", NamedTextColor.GOLD));
        help = help.append(Component.text("\n/islandadmin remove <player> <island owner>", NamedTextColor.GOLD));
        help = help.append(Component.text("\n/islandadmin listworld", NamedTextColor.GOLD));
        sender.sendMessage(help);
    }
}
