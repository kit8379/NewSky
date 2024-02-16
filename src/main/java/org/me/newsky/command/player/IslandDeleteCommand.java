package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseDeleteCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.Optional;
import java.util.UUID;

public class IslandDeleteCommand extends BaseDeleteCommand {

    public IslandDeleteCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    protected boolean isOwner(CommandSender sender, UUID islandUuid) {
        Player player = (Player) sender;
        Optional<UUID> islandOwnerOpt = cacheHandler.getIslandOwner(islandUuid);
        if (islandOwnerOpt.isEmpty() || !islandOwnerOpt.get().equals(player.getUniqueId())) {
            sender.sendMessage(config.getPlayerNotOwnerMessage());
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getIslandDeleteWarningMessage(String[] args) {
        return config.getPlayerDeleteWarningMessage();
    }

    @Override
    protected String getIslandDeleteSuccessMessage(String[] args) {
        return config.getPlayerDeleteSuccessMessage();
    }
}
