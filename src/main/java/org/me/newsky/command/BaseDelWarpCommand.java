package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseDelWarpCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseDelWarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Get the target warp name
        String warpName = args[getTargetWarpArgIndex()];

        // Check if the target player has an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Check if the player has the target warp point
        Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(islandUuid, targetUuid, warpName);
        if (warpLocationOpt.isEmpty()) {
            sender.sendMessage(getNoWarpMessage(args));
            return true;
        }

        // Delete the warp point
        cacheHandler.deleteWarpPoint(islandUuid, targetUuid, warpName);

        // Send the success message
        sender.sendMessage(getDelWarpSuccessMessage(args));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getNoWarpMessage(String[] args);

    protected abstract String getDelWarpSuccessMessage(String[] args);
}
