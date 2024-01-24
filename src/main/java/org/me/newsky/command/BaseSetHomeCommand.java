package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseSetHomeCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseSetHomeCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

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

        // Get the home name from the command arguments
        String homeName = args[getTargetHomeArgIndex()];

        // Get the home location
        String homeLocation = player.getLocation().toVector().toString();

        // Add or update the home point
        cacheHandler.addOrUpdateHomePoint(targetUuid, homeName, homeLocation);

        // Send the success message
        sender.sendMessage(getSetHomeSuccessMessage(homeName));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract int getTargetHomeArgIndex();

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getMustInIslandMessage(String[] args);

    protected abstract String getSetHomeSuccessMessage(String homeName);
}
