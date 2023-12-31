package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseHomeCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseHomeCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
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
            sender.sendMessage(config.getPlayerNoIslandMessage(args[1]));
            return true;
        }

        // Teleport to the island

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);
}
