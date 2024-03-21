package org.me.newsky.command.sub.admin;

import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseLoadCommand;
import org.me.newsky.config.ConfigHandler;

public class AdminLoadCommand extends BaseLoadCommand {

    public AdminLoadCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(config.getUsagePrefix() + getUsageCommandMessage());
            return false;
        }
        return true;
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminLoadUsageMessage();
    }
}
