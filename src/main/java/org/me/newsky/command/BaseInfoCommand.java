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
        UUID targetUuid = getTargetUuid(sender, args);
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);

        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage("You have no island.");
            return true;
        }

        UUID islandUuid = islandUuidOpt.get();
        Optional<UUID> ownerUuidOpt = cacheHandler.getIslandOwner(islandUuid);

        if (ownerUuidOpt.isEmpty()) {
            sender.sendMessage("Your island has no owner.");
            return true;
        }

        UUID ownerUuid = ownerUuidOpt.get();
        Set<UUID> memberUuids = cacheHandler.getIslandMembers(islandUuid);
        String membersString = buildMembersString(memberUuids);

        sender.sendMessage("Island Owner: " + Bukkit.getOfflinePlayer(ownerUuid).getName());
        sender.sendMessage("Island Members: " + membersString);

        return true;
    }

    private String buildMembersString(Set<UUID> memberUuids) {
        return memberUuids.stream()
                .map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
                .collect(Collectors.joining(", "));
    }

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
}
