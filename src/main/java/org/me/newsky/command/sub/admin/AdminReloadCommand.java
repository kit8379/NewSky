package org.me.newsky.command.sub.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.NewSky;
import org.me.newsky.command.sub.BaseReloadCommand;
import org.me.newsky.config.ConfigHandler;

public class AdminReloadCommand extends BaseReloadCommand {

    public AdminReloadCommand(NewSky plugin, ConfigHandler config) {
        super(plugin, config);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminReloadUsageMessage();
    }
}
