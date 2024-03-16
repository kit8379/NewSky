package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.base.BasePvpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminPvpCommand extends BasePvpCommand {

    public AdminPvpCommand(ConfigHandler config, NewSkyAPI api) {
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
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getAdminNoIslandMessage(args[1]);
    }

    @Override
    protected String getIslandPvpEnableSuccessMessage(String[] args) {
        return config.getAdminPvpEnableSuccessMessage(args[1]);
    }

    @Override
    protected String getIslandPvPDisableSuccessMessage(String[] args) {
        return config.getAdminPvpDisableSuccessMessage(args[1]);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminPvpUsageMessage();
    }
}
