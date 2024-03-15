package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.base.BaseUnloadCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

public class AdminUnloadCommand extends BaseUnloadCommand {

    public AdminUnloadCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminUnloadUsageMessage();
    }
}
