package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseAddMemberCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseAddMemberCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        OfflinePlayer targetAdd = Bukkit.getOfflinePlayer(args[getTargetAddArgIndex()]);
        UUID islandOwnerId = getIslandOwnerUuid(sender, args);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);

        // Check if the target island owner has an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage(config.getPlayerNoIslandMessage(args[getTargetAddArgIndex()]));
            return true;
        }

        // Check if the target player is the island owner
        if (targetAdd.getUniqueId().equals(islandOwnerId)) {
            sender.sendMessage(config.getPlayerAlreadyIslandOwnerMessage(args[getTargetAddArgIndex()]));
            return true;
        }

        // Check if the target player is already a member of the island
        if (cacheHandler.getIslandMembers(islandUuid.get()).contains(targetAdd.getUniqueId())) {
            sender.sendMessage(config.getPlayerAlreadyMemeberMessage(args[getTargetAddArgIndex()]));
            return true;
        }

        // Add the target player to the island

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract int getTargetAddArgIndex();
    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);
}
