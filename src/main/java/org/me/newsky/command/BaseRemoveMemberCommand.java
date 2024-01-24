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
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the island owner's UUID
        UUID islandOwnerId = getIslandOwnerUuid(sender, args);

        // Check if the island owner has an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Get the target player's UUID
        OfflinePlayer targetRemove = Bukkit.getOfflinePlayer(args[getTargetRemoveArgIndex()]);

        // Check if the target player is a member of the island
        if (!cacheHandler.getIslandMembers(islandUuid).contains(targetRemove.getUniqueId())) {
            sender.sendMessage(targetRemove.getName() + " is not a member of the island.");
            return true;
        }

        // Check if the target player is the island owner
        if (targetRemove.getUniqueId().equals(islandOwnerId)) {
            sender.sendMessage("You cannot remove the island owner.");
            return true;
        }

        // Remove the target player from the island
        cacheHandler.deleteIslandPlayer(targetRemove.getUniqueId(), islandUuid);

        // Send the success message
        sender.sendMessage(getIslandRemoveMemberSuccessMessage(args));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract int getTargetRemoveArgIndex();

    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandRemoveMemberSuccessMessage(String[] args);
}
