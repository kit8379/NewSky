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
        if (args.length < 2) {
            sender.sendMessage(config.getAdminUnloadUsageMessage());
            return false;
        }

        // Get the target player's UUID
        UUID targetUuid = Bukkit.getOfflinePlayer(args[1]).getUniqueId();

        // Get the target player's island UUID
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(config.getNoIslandMessage(args[1]));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Run the island unload future
        CompletableFuture<Void> unloadIslandFuture = islandHandler.unloadIsland(islandUuid);
        handleIslandUnloadFuture(unloadIslandFuture, sender, args);

        return true;
    }

    protected void handleIslandUnloadFuture(CompletableFuture<Void> future, CommandSender sender, String[] args) {
        future.thenRun(() -> {
            // Send the success message
            sender.sendMessage(config.getIslandUnloadSuccessMessage(args[1]));
        }).exceptionally(ex -> {
            if (ex instanceof IllegalStateException) {
                sender.sendMessage(ex.getMessage());
            } else {
                ex.printStackTrace();
                sender.sendMessage("There was an error creating the island.");
            }
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
}
