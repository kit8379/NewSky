package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseDeleteCommand {

    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseDeleteCommand(CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        UUID targetUuid = getTargetUuid(sender, args);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);

        if (islandUuid.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }

        // Delete island
        islandHandler.deleteWorld(islandUuid.get().toString());
        cacheHandler.deleteIsland(islandUuid.get());
        sender.sendMessage(getIslandDeletedMessage(args));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
    protected abstract String getNoIslandMessage(String[] args);
    protected abstract String getIslandDeletedMessage(String[] args);
}
