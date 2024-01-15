package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseRemoveMemberCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseRemoveMemberCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        OfflinePlayer targetRemove = Bukkit.getOfflinePlayer(args[getTargetRemoveArgIndex()]);
        UUID islandOwnerId = getIslandOwnerUuid(sender, args);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);

        // Check if the target island owner has an island
        if (islandUuid.isEmpty()) {
            sender.sendMessage(config.getPlayerNoIslandMessage(Bukkit.getOfflinePlayer(islandOwnerId).getName()));
            return true;
        }

        // Check if the target player is a member of the island
        if (!cacheHandler.getIslandMembers(islandUuid.get()).contains(targetRemove.getUniqueId())) {
            sender.sendMessage(config.getPlayerNotMemberMessage(targetRemove.getName()));
            return true;
        }

        Optional<UUID> ownerUuid = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);
        if (ownerUuid.isEmpty()) {
            sender.sendMessage(config.getNoIslandOwnerMessage());
            return true;
        }

        // Check if the target player is the owner of the island
        if (ownerUuid.get().equals(targetRemove.getUniqueId())) {
            sender.sendMessage(config.getCannotDeleteOwnerMessage());
            return true;
        }

        // Remove the target player from the island
        cacheHandler.deleteIslandPlayer(targetRemove.getUniqueId(), islandUuid.get());
        sender.sendMessage(config.getPlayerIslandRemovedMessage(targetRemove.getName(), Bukkit.getOfflinePlayer(islandOwnerId).getName()));

        return true;
    }


    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract int getTargetRemoveArgIndex();
    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);
}