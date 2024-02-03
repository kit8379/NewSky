package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseUnloadCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseUnloadCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        UUID targetUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        // Get the target player's island UUID
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(args[1] + " does not have an island.");
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Run the island unload future
        CompletableFuture<Void> deleteIslandFuture = islandHandler.unloadIsland(islandUuid);
        handleIslandUnloadFuture(deleteIslandFuture, sender, args);

        return true;
    }

    protected void handleIslandUnloadFuture(CompletableFuture<Void> future, CommandSender sender, String[] args) {
        future.thenRun(() -> {
            // Send the success message
            sender.sendMessage(args[1] + "'s island has been unloaded.");
        }).exceptionally(ex -> {
            // Send the error message
            sender.sendMessage("There was an error unloading the island.");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
}