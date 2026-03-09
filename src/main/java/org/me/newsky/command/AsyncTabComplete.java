package org.me.newsky.command;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Subcommand-level async tab completion.
 * Example: /is removemember <player>
 */
public interface AsyncTabComplete {
    CompletableFuture<List<String>> tabCompleteAsync(CommandSender sender, String label, String[] args);
}