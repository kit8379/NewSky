package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseReloadCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

public class AdminReloadCommand extends BaseReloadCommand {



    public AdminReloadCommand(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(plugin, config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(config.getAdminReloadCommandUsage());
            return false;
        }
        return true;
    }
}
