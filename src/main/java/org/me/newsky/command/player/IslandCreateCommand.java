package org.me.newsky.command.player;

import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.me.newsky.command.BaseCreateCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class IslandCreateCommand extends BaseCreateCommand {

    public IslandCreateCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getExistingIslandMessage(String[] args) {
        return config.getHasIslandMessage();
    }

    @Override
    protected String getIslandCreatedMessage(String[] args) {
        return config.getIslandCreatedMessage();
    }

    @Override
    protected void performPostCreationActions(CommandSender sender, UUID targetUuid, UUID islandUuid) {
        islandHandler.teleportToIsland((Player) sender, islandUuid.toString());
    }
}
