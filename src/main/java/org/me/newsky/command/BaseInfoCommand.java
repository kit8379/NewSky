package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class BaseInfoCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseInfoCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        UUID targetUuid = getTargetUuid(sender, args);
        Optional<UUID> islandUuid = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);

        if (islandUuid.isEmpty()) {
            sender.sendMessage(config.getPlayerNoIslandMessage(args[1]));
            return true;
        }

        Optional<UUID> ownerUuid = cacheHandler.getIslandOwner(islandUuid.get());

        if (ownerUuid.isEmpty()) {
            sender.sendMessage(config.getNoIslandOwnerMessage());
            return true;
        }

        Set<UUID> memberUuids = cacheHandler.getIslandMembers(islandUuid.get());
        StringBuilder membersString = buildMembersString(memberUuids);

        sender.sendMessage("Island Info");
        sender.sendMessage("Island UUID: " + islandUuid.get());
        sender.sendMessage("Island Owner: " + Bukkit.getOfflinePlayer(ownerUuid.get()).getName());
        sender.sendMessage("Island Members: " + membersString);

        return true;
    }

    private StringBuilder buildMembersString(Set<UUID> memberUuids) {
        StringBuilder membersString = new StringBuilder();
        for (UUID memberUuid : memberUuids) {
            String memberName = Bukkit.getOfflinePlayer(memberUuid).getName();
            membersString.append(memberName).append(", ");
        }
        if (membersString.length() > 0) {
            membersString = new StringBuilder(membersString.substring(0, membersString.length() - 2));
        }
        return membersString;
    }

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
}
