package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;

import org.me.newsky.NewSky;
import org.me.newsky.command.IslandSubCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

public class AdminReloadCommand implements IslandSubCommand {

    private final NewSky plugin;

    public AdminReloadCommand(NewSky plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        plugin.reload();
        sender.sendMessage("Reloaded NewSky.");
        return true;
    }
}
