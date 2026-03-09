package org.me.newsky.command;

import org.bukkit.command.CommandSender;



public interface SubCommand {
    String getName();

    String[] getAliases();

    String getPermission();

    String getSyntax();

    String getDescription();

    boolean execute(CommandSender sender, String[] args);
}
