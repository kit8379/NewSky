package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.command.SubCommand;
import org.me.newsky.config.ConfigHandler;


/**
 * /isadmin reload
 */
public class AdminReloadCommand implements SubCommand {
    private final NewSky plugin;
    private final ConfigHandler config;

    public AdminReloadCommand(NewSky plugin, ConfigHandler config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String[] getAliases() {
        return config.getAdminReloadAliases();
    }

    @Override
    public String getPermission() {
        return config.getAdminReloadPermission();
    }

    @Override
    public String getSyntax() {
        return config.getAdminReloadSyntax();
    }

    @Override
    public String getDescription() {
        return config.getAdminReloadDescription();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        plugin.reload();
        sender.sendMessage(config.getPluginReloadedMessage());
        return true;
    }
}
