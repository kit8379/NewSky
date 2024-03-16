package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.base.BaseSetHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminSetHomeCommand extends BaseSetHomeCommand {

    public AdminSetHomeCommand(ConfigHandler config, NewSkyAPI api) {
        super(config, api);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(config.getUsagePrefix() + getUsageCommandMessage());
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected int getTargetHomeArgIndex() {
        return 2;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getAdminNoIslandMessage(args[1]);
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return config.getAdminMustInIslandSetHomeMessage(args[1]);
    }

    @Override
    protected String getSetHomeSuccessMessage(String[] args, String homeName) {
        return config.getAdminSetHomeSuccessMessage(args[1], homeName);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminSetHomeUsageMessage();
    }
}
