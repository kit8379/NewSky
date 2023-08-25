package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.command.BaseDeleteCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class AdminDeleteCommand extends BaseDeleteCommand {

    public AdminDeleteCommand(CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /islandadmin delete <player>");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return args[1] + " doesn't have an island.";
    }

    @Override
    protected String getIslandDeletedMessage(String[] args) {
        return "Deleted " + args[1] + "'s island.";
    }
}
