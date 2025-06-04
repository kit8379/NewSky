package org.me.newsky.command;

import org.bukkit.command.CommandSender;


/**
 * Defines the structure for subcommands in the NewSky plugin.
 * Each subcommand must implement this interface to provide
 * its name, aliases, permission requirements,
 * syntax, description, and execution logic.
 */
public interface SubCommand {
    String getName();

    String[] getAliases();

    String getPermission();

    String getSyntax();

    String getDescription();

    boolean execute(CommandSender sender, String[] args);
}
