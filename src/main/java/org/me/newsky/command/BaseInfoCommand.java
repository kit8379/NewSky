package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class BaseInfoCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseInfoCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Check if the target player has an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(config.getNoIslandMessage(Bukkit.getOfflinePlayer(targetUuid).getName()));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Get the island owner's UUID
        Optional<UUID> ownerUuidOpt = cacheHandler.getIslandOwner(islandUuid);
        if (ownerUuidOpt.isEmpty()) {
            sender.sendMessage(config.getNoOwnerMessage());
            return true;
        }
        UUID ownerUuid = ownerUuidOpt.get();

        // Get the island members' UUIDs
        Set<UUID> memberUuids = cacheHandler.getIslandMembers(islandUuid);

        // Send the island info
        String membersString = buildMembersString(memberUuids);

        // Send the island info
        sender.sendMessage(config.getIslandIDMessage(islandUuid.toString()));
        sender.sendMessage(config.getIslandOwnerMessage(Bukkit.getOfflinePlayer(ownerUuid).getName()));
        sender.sendMessage(config.getIslandMembersMessage(membersString));

        return true;
    }

    private String buildMembersString(Set<UUID> memberUuids) {
        return memberUuids.stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).collect(Collectors.joining("\n"));
    }

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
}
