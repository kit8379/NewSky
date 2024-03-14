package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseDelWarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminDelWarpCommand extends BaseDelWarpCommand {

    public AdminDelWarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 3) {
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
    protected String getNoWarpMessage(String[] args) {
        return config.getAdminNoWarpMessage(args[1], args[2]);
    }

    @Override
    protected String getDelWarpSuccessMessage(String[] args) {
        return config.getAdminDelWarpSuccessMessage(args[1], args[2]);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminDelWarpUsageMessage();
    }
}
