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
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        OfflinePlayer targetAdd = Bukkit.getOfflinePlayer(args[getTargetAddArgIndex()]);

        // Get the island owner's UUID
        UUID islandOwnerId = getIslandOwnerUuid(sender, args);

        // Get the island UUID from the island owner's UUID
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Check if the target player is already the owner of the island
        if (targetAdd.getUniqueId().equals(islandOwnerId)) {
            sender.sendMessage(targetAdd.getName() + " is already the owner of the island.");
            return true;
        }

        // Check if the target player is already a member of the island
        if (cacheHandler.getIslandMembers(islandUuid).contains(targetAdd.getUniqueId())) {
            sender.sendMessage(targetAdd.getName() + " is already a member of the island.");
            return true;
        }

        // Set the spawn location
        String spawnLocation = "0,100,0,100,100";
        // Set the role
        String role = "member";

        // Add the target player to the island
        cacheHandler.addIslandPlayer(targetAdd.getUniqueId(), islandUuid, role);
        // Send the success message
        sender.sendMessage(getIslandAddMemberSuccessMessage(args));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract int getTargetAddArgIndex();
    protected abstract UUID getIslandOwnerUuid(CommandSender sender, String[] args);
    protected abstract String getNoIslandMessage(String[] args);
    protected abstract String getIslandAddMemberSuccessMessage(String[] args);
}
