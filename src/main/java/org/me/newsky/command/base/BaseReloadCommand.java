package org.me.newsky.command.base;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.command.BaseCommand;
import org.me.newsky.config.ConfigHandler;

public abstract class BaseReloadCommand implements BaseCommand {

    protected final NewSky plugin;
    protected final ConfigHandler config;

    public BaseReloadCommand(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean execute(CommandSender sender, String[] args) {
        // Check if the command arguments are valid
        if (!validateArgs(sender, args)) {
            return true;
        }

        // Reload the plugin
        plugin.reload();

        // Send the success message
        sender.sendMessage(config.getPluginReloadedMessage());

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
}
