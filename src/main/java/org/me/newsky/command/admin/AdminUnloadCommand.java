package org.me.newsky.command.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.base.BaseUnloadCommand;
import org.me.newsky.config.ConfigHandler;

public class AdminUnloadCommand extends BaseUnloadCommand {

    public AdminUnloadCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
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
