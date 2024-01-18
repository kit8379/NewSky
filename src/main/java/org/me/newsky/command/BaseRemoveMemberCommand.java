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
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);

        if (islandUuidOpt.isEmpty()) {
            String islandOwnerName = Bukkit.getOfflinePlayer(islandOwnerId).getName();
            sender.sendMessage(islandOwnerName + " has no island.");
            return true;
        }

        UUID islandUuid = islandUuidOpt.get();

        // Check if the target player is not a member of the island
        if (!cacheHandler.getIslandMembers(islandUuid).contains(targetRemove.getUniqueId())) {
            String islandOwnerName = Bukkit.getOfflinePlayer(islandOwnerId).getName();
            sender.sendMessage(targetRemove.getName() + " is not a member of " + islandOwnerName + "'s island.");
            return true;
        }

        // Check if the target player is the owner of the island
        if (targetRemove.getUniqueId().equals(islandOwnerId)) {
            sender.sendMessage("You cannot remove the island owner.");
            return true;
        }

        // Remove the target player from the island
        cacheHandler.deleteIslandPlayer(targetRemove.getUniqueId(), islandUuid);
        String islandOwnerName = Bukkit.getOfflinePlayer(islandOwnerId).getName();
        sender.sendMessage("Removed " + targetRemove.getName() + " from " + islandOwnerName + "'s island.");

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract int getTargetRemoveArgIndex();
    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);
}
