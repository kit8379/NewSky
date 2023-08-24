package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public abstract class BaseCreateCommand {

    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseCreateCommand(CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        UUID targetUuid = getTargetUuid(sender, args);

        // Check if player already has an island
        if (cacheHandler.getIslandUuidByPlayerUuid(targetUuid).isPresent()) {
            sender.sendMessage(getExistingIslandMessage(args));
            return true;
        }

        // Generate island UUID
        UUID islandUuid = UUID.randomUUID();

        // Create island
        islandHandler.createWorld(islandUuid.toString());
        cacheHandler.createIsland(islandUuid, targetUuid);
        performPostCreationActions(sender, targetUuid, islandUuid);

        sender.sendMessage("Island created.");
        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
    protected abstract String getExistingIslandMessage(String[] args);
    protected abstract void performPostCreationActions(CommandSender sender, UUID targetUuid, UUID islandUuid);
}
