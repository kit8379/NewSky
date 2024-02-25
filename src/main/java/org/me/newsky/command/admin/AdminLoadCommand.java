package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseLoadCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

public class AdminLoadCommand extends BaseLoadCommand {

    public AdminLoadCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }
}
