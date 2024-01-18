package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

public abstract class BaseReloadCommand {

    protected final NewSky plugin;
    protected final ConfigHandler config;

    public BaseReloadCommand(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        plugin.reload();
        sender.sendMessage("§aPlugin reloaded");
        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
}
