package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseSetwarpCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseSetwarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!validateArgs(sender, args)) {
            return true;
        }

        Player player = (Player) sender;
        UUID targetUuid = getTargetUuid(sender, args);
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);

        // Check if the player has an island
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }

        UUID islandUuid = islandUuidOpt.get();

        // Check if the player is in the correct island world
        String islandWorldName = "island-" + islandUuid;
        if (!player.getWorld().getName().equals(islandWorldName)) {
            sender.sendMessage(getMustInIslandMessage(args));
            return true;
        }

        // Get the location where the player wants to set the warp
        String warpLocation = player.getLocation().toVector().toString();

        // Set the warp point for the island
        cacheHandler.addOrUpdateWarpPoint(targetUuid, islandUuid, warpLocation);

        sender.sendMessage(getSetWarpSuccessMessage(args));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
    protected abstract String getNoIslandMessage(String[] args);
    protected abstract String getMustInIslandMessage(String[] args);
    protected abstract String getSetWarpSuccessMessage(String[] args);
}
