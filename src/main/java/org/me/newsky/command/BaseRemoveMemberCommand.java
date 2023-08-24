package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseRemoveMemberCommand {

    protected final CacheHandler cacheHandler;

    public BaseRemoveMemberCommand(CacheHandler cacheHandler) {
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
            sender.sendMessage(Bukkit.getOfflinePlayer(islandOwnerId).getName() + " doesn't have an island.");
            return true;
        }

        // Check if the target player is a member of the island
        if (!cacheHandler.getIslandMembers(islandUuid.get()).contains(targetRemove.getUniqueId())) {
            sender.sendMessage(targetRemove.getName() + " is not a member of the island.");
            return true;
        }

        // Check if the target player is the owner of the island
        if (cacheHandler.getIslandOwner(islandUuid.get()).equals(targetRemove.getUniqueId())) {
            sender.sendMessage("You can't remove the owner of the island.");
            return true;
        }

        // Remove the target player from the island
        cacheHandler.removeIslandMember(islandUuid.get(), targetRemove.getUniqueId());
        sender.sendMessage("Removed " + targetRemove.getName() + " from " + Bukkit.getOfflinePlayer(islandOwnerId).getName() + "'s island.");

        return true;
    }


    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract int getTargetRemoveArgIndex();
    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);
}