package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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

        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getNoConsoleMessage());
            return true;
        }

        UUID targetUuid = getTargetUUID(sender, args);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);

        // Check if the target island owner has an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage(config.getPlayerNoIslandMessage(Bukkit.getOfflinePlayer(targetUuid).getName()));
            return true;
        }

        performPostCreationActions(sender, targetUuid, islandUuid.get());
        return true;
    }


    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUUID(CommandSender sender, String[] args);
    protected abstract void performPostCreationActions(CommandSender sender, UUID targetUuid, UUID islandUuid);
}
