package org.me.newsky.command.base;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class BaseLockCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseLockCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
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
            CompletableFuture<Void> future = islandHandler.lockIsland(islandUuid);
            future.thenRun(() -> {
                sender.sendMessage(getIslandLockSuccessMessage(args));
            });
        }

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandUnLockSuccessMessage(String[] args);

    protected abstract String getIslandLockSuccessMessage(String[] args);
}
