package org.me.newsky.command.base;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseAddMemberCommand implements BaseCommand {

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

        // Get the island owner's UUID
        UUID islandOwnerId = getIslandOwnerUuid(sender, args);

        // Get the island UUID from the island owner's UUID
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(islandOwnerId);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Get the target player's UUID
        OfflinePlayer targetAdd = Bukkit.getOfflinePlayer(args[getTargetAddArgIndex()]);
        UUID targetUuid = targetAdd.getUniqueId();

        // Check if the target player is already a member of the island
        if (cacheHandler.getIslandMembers(islandUuid).contains(targetUuid)) {
            sender.sendMessage(config.getIslandMemberExistsMessage(targetAdd.getName()));
            return true;
        }

        // Set the spawn location
        int spawnX = config.getIslandSpawnX();
        int spawnY = config.getIslandSpawnY();
        int spawnZ = config.getIslandSpawnZ();
        float spawnYaw = config.getIslandSpawnYaw();
        float spawnPitch = config.getIslandSpawnPitch();
        String spawnLocation = spawnX + "," + spawnY + "," + spawnZ + "," + spawnYaw + "," + spawnPitch;

        // Set the member role
        String role = "member";

        // Add the target player to the island
        cacheHandler.updateIslandPlayer(targetUuid, islandUuid, role);
        // Add the player default spawn
        cacheHandler.updateHomePoint(targetUuid, islandUuid, "default", spawnLocation);
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
