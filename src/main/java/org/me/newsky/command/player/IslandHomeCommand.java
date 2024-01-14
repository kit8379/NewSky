package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.command.BaseHomeCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class IslandHomeCommand extends BaseHomeCommand {

    public IslandHomeCommand(CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("Usage: /island home");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUUID(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected void performPostCreationActions(CommandSender sender, UUID targetUuid, UUID islandUuid) {
        islandHandler.teleportToIsland((Player) sender, islandUuid.toString());
    }
}
