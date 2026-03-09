package org.me.newsky.command;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Root-command level async tab-complete router.
 * Example: /is, /isadmin
 */
public interface AsyncCommandTabRouter {
    CompletableFuture<List<String>> completeAsync(CommandSender sender, String label, String[] args);
}