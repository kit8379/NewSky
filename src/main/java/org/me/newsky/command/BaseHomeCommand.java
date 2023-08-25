package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseHomeCommand {

    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseHomeCommand(CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        UUID targetUUID = getTargetUUID(sender, args);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetUUID);

        // Check if the target island owner has an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage(Bukkit.getOfflinePlayer(targetUUID).getName() + " doesn't have an island.");
            return true;
        }

        performPostCreationActions(sender, targetUUID, islandUuid.get());
        return true;
    }


    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);
    protected abstract void performPostCreationActions(CommandSender sender, UUID targetUuid, UUID islandUuid);
}
