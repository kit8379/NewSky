package org.me.newsky.command.sub.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.api.NewSkyAPI;
import org.me.newsky.command.sub.BaseSetWarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminSetWarpCommand extends BaseSetWarpCommand {

    public AdminSetWarpCommand(ConfigHandler config, NewSkyAPI api) {
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
    protected int getTargetWarpArgIndex() {
        return 2;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getAdminNoIslandMessage(args[1]);
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return config.getAdminMustInIslandSetWarpMessage(args[1]);
    }

    @Override
    protected String getSetWarpSuccessMessage(String[] args, String warpName) {
        return config.getAdminSetWarpSuccessMessage(args[1], warpName);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminSetWarpUsageMessage();
    }
}