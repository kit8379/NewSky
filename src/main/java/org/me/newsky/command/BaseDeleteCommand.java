package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseDeleteCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseDeleteCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        UUID targetUuid = getTargetUuid(sender, args);
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);

        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }

        UUID islandUuid = islandUuidOpt.get();

        CompletableFuture<Void> deleteIslandFuture = islandHandler.deleteIsland(islandUuid.toString());
        handleIslandDeletionFuture(deleteIslandFuture, sender, islandUuid, args);

        return true;
    }

    protected void handleIslandDeletionFuture(CompletableFuture<Void> future, CommandSender sender, UUID islandUuid, String[] args) {
        future.thenRun(() -> {
            cacheHandler.deleteIsland(islandUuid);
            sender.sendMessage(getIslandDeletedMessage(args));
        }).exceptionally(ex -> {
            sender.sendMessage("There was an error deleting the island.");
            return null;
        });
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandDeletedMessage(String[] args);
}
