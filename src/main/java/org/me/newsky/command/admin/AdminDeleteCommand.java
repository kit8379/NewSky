package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.handler.CacheHandler;

public class AdminDeleteCommand implements IslandSubCommand {
    private final NewSky plugin;
    private final CacheHandler cacheHandler;

    public AdminDeleteCommand(NewSky plugin) {
        this.plugin = plugin;
        this.cacheHandler = plugin.getCacheHandler();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        cacheHandler.deleteIsland(target.getUniqueId());
        sender.sendMessage("Island deleted.");
        return true;
    }
}
