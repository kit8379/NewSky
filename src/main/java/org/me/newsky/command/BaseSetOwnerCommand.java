package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseSetOwnerCommand {

    protected final ConfigHandler config;

    protected final CacheHandler cacheHandler;

    public BaseSetOwnerCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
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

        if (!isOwner(sender, islandUuid)) {
            return true;
        }

        // Get the target player's UUID
        OfflinePlayer targetOwner = Bukkit.getOfflinePlayer(args[getTargetOwnerArgIndex()]);
        UUID targetUuid = targetOwner.getUniqueId();

        // Check if the target player is a member of the island
        if (!cacheHandler.getIslandMembers(islandUuid).contains(targetUuid)) {
            sender.sendMessage(config.getNotIslandMemberMessage(targetOwner.getName()));
            return true;
        }

        // Check if the target player is already a owner
        if (cacheHandler.getIslandOwner(islandUuid).isPresent() && cacheHandler.getIslandOwner(islandUuid).get().equals(targetUuid)) {
            sender.sendMessage(getAlreadyOwnerMessage(args));
            return true;
        }

        // Set the new owner
        cacheHandler.updateIslandOwner(targetUuid, islandUuid);
        sender.sendMessage(getSetOwnerSuccessMessage(args));
        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract boolean isOwner(CommandSender sender, UUID islandUuid);

    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);

    protected abstract int getTargetOwnerArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getAlreadyOwnerMessage(String[] args);

    protected abstract String getSetOwnerSuccessMessage(String[] args);
}
