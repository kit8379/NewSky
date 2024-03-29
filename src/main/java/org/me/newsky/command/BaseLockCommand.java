package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseLockCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseLockCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Cast the sender to a player
        Player player = (Player) sender;

        // Get the target UUID
        UUID targetUuid = player.getUniqueId();

        // Check if the player has an island and get the island UUID
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Check if the island is locked and update the lock status
        if (cacheHandler.getIslandLock(islandUuid)) {
            cacheHandler.updateIslandLock(islandUuid, false);
            sender.sendMessage(getIslandUnLockSuccessMessage(args));
        } else {
            cacheHandler.updateIslandLock(islandUuid, true);
            sender.sendMessage(getIslandLockSuccessMessage(args));
        }

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandUnLockSuccessMessage(String[] args);

    protected abstract String getIslandLockSuccessMessage(String[] args);
}
