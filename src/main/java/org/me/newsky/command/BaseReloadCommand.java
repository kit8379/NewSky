package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

public abstract class BaseReloadCommand {

    protected final NewSky plugin;
    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;
    protected final IslandHandler islandHandler;

    public BaseReloadCommand(NewSky plugin, ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.plugin = plugin;
        this.config = config;
        this.cacheHandler = cacheHandler;
        this.islandHandler = islandHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        plugin.reload();
        sender.sendMessage(config.getReloadMessage());
        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
}
