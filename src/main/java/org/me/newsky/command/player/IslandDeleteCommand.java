package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.command.BaseDeleteCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.UUID;

public class IslandDeleteCommand extends BaseDeleteCommand {

    public IslandDeleteCommand(CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(cacheHandler, islandHandler);
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
    protected String getNoIslandMessage(String[] args) {
        return "You don't have an island.";
    }

    @Override
    protected String getIslandDeletedMessage(String[] args) {
        return "Island deleted.";
    }
}
