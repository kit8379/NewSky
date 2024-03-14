package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseDeleteCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;
    private final Confirmation confirmations = new Confirmation();

    public BaseDeleteCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
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
        UUID targetUuid = getTargetUuid(sender, args);

        // Get the target player's island UUID
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Check if the sender is the owner of the island
        if (!isOwner(sender, islandUuid)) {
            return true;
        }

        // Double confirmation check
        String commandName = "delete"; // Use a constant or a method to get the command name
        if (commandName.equals(confirmations.getIfPresent(targetUuid))) {
            confirmations.invalidate(targetUuid);
            // Run the island deletion future
            CompletableFuture<Void> deleteIslandFuture = islandHandler.deleteIsland(islandUuid);
            handleIslandDeletionFuture(deleteIslandFuture, sender, args);
        } else {
            confirmations.put(targetUuid, commandName);
            sender.sendMessage(getIslandDeleteWarningMessage(args));
        }


        return true;
    }

    protected void handleIslandDeletionFuture(CompletableFuture<Void> future, CommandSender sender, String[] args) {
        future.thenRun(() -> {
            // Send the success message
            sender.sendMessage(getIslandDeleteSuccessMessage(args));
        }).exceptionally(ex -> {
            // Send the error message
            sender.sendMessage("There was an error creating the island.");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract boolean isOwner(CommandSender sender, UUID islandUuid);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandDeleteWarningMessage(String[] args);

    protected abstract String getIslandDeleteSuccessMessage(String[] args);
}
