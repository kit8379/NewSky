package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        cacheHandler.deleteWarpPoint(targetUuid, islandUuid, warpName);

        // Send the success message
        sender.sendMessage(getDelWarpSuccessMessage(args));

        return true;
    }

    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == getTargetWarpArgIndex() + 1) {
            UUID targetUuid = getTargetUuid(sender, args);
            Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
            if (islandUuidOpt.isEmpty()) {
                return null;
            }
            UUID islandUuid = islandUuidOpt.get();
            Set<String> warpNames = cacheHandler.getWarpNames(islandUuid, targetUuid);
            return warpNames.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[getTargetWarpArgIndex()].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getNoWarpMessage(String[] args);

    protected abstract String getDelWarpSuccessMessage(String[] args);
}
