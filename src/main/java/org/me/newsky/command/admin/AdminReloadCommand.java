package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

public class AdminReloadCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;


    public AdminReloadCommand(NewSky plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigHandler();
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if(args.length != 1) {
            sender.sendMessage("Usage: /islandadmin reload");
            return true;
        }

        config.reload();
        sender.sendMessage("Config reloaded.");
        return true;
    }
}
