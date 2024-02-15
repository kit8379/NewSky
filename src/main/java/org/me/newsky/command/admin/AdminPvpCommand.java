package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BasePvpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminPvpCommand extends BasePvpCommand {

    public AdminPvpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(config.getAdminPvpUsageMessage());
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
}
