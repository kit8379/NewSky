package org.me.newsky.command;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseSetWarpCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseSetWarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Cast the sender to a player
        Player player = (Player) sender;

        // Get the target player's UUID
        UUID targetUuid = getTargetUuid(sender, args);

        // Check if the player has an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Check if the player is currently in the target island world
        if (!player.getWorld().getName().equals("island-" + islandUuid)) {
            sender.sendMessage(getMustInIslandMessage(args));
            return true;
        }

        // Get the target warp name
        String warpName = args[getTargetWarpArgIndex()];

        // Set the warp point
        Location loc = player.getLocation();
        String warpLocation = String.format("%.1f,%.1f,%.1f,%.1f,%.1f",
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch());

        // Add the warp point to the cache
        cacheHandler.addOrUpdateWarpPoint(targetUuid, warpName, warpLocation);

        // Send the success message
        sender.sendMessage(getSetWarpSuccessMessage(args));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetWarpArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getMustInIslandMessage(String[] args);

    protected abstract String getSetWarpSuccessMessage(String[] args);
}
