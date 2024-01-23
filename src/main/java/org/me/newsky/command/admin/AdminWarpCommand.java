package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseWarpCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

public class AdminWarpCommand extends BaseWarpCommand {

    public AdminWarpCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        // Allow one or two arguments: /islandadmin warp <player> [warpName]
        if (args.length != 3) {
            sender.sendMessage("§eUsage: §b/islandadmin warp <player> [warpName]");
            return false;
        }
        return true;
    }
}
