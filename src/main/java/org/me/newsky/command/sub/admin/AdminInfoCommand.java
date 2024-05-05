package org.me.newsky.command.sub.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseInfoCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminInfoCommand extends BaseInfoCommand {

    public AdminInfoCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminInfoUsageMessage();
    }
}
