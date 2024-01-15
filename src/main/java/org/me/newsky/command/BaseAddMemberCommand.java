package org.me.newsky.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseAddMemberCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseAddMemberCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
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
            sender.sendMessage(config.getPlayerNoIslandMessage(Bukkit.getOfflinePlayer(islandOwnerId).getName()));
            return true;
        }

        // Check if the target player is the island owner
        if (targetAdd.getUniqueId().equals(islandOwnerId)) {
            sender.sendMessage(config.getPlayerAlreadyIslandOwnerMessage(targetAdd.getName()));
            return true;
        }

        // Check if the target player is already a member of the island
        if (cacheHandler.getIslandMembers(islandUuid.get()).contains(targetAdd.getUniqueId())) {
            sender.sendMessage(config.getPlayerAlreadyMemberMessage(targetAdd.getName()));
            return true;
        }

        // Add the target player to the island
        String spawnLocation = "0,100,0,100,100";
        String role = "member";
        cacheHandler.addIslandPlayer(targetAdd.getUniqueId(), islandUuid.get(), spawnLocation, role);
        sender.sendMessage(config.getPlayerIslandAddedMessage(targetAdd.getName(), Bukkit.getOfflinePlayer(islandOwnerId).getName()));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract int getTargetAddArgIndex();
    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);
}
