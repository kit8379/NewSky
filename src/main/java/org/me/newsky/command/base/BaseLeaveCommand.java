package org.me.newsky.command.base;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseLeaveCommand implements BaseCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseLeaveCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage(config.getOnlyPlayerCanRunCommandMessage());
            return true;
        }

        Player player = (Player) sender;
        UUID playerUuid = player.getUniqueId();

        // Check if the player is a member of an island
        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(playerUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(config.getPlayerNoIslandMessage());
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        // Check if the player is the island owner
        Optional<UUID> ownerUuidOpt = cacheHandler.getIslandOwner(islandUuid);
        if (ownerUuidOpt.isPresent() && ownerUuidOpt.get().equals(playerUuid)) {
            sender.sendMessage(config.getPlayerCannotLeaveAsOwnerMessage());
            return true;
        }

        // Remove the player from the island
        cacheHandler.deleteIslandPlayer(playerUuid, islandUuid);

        // Send the success message
        sender.sendMessage(config.getPlayerLeaveSuccessMessage());

        return true;
    }
}
