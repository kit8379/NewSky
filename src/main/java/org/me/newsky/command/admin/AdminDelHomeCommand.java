package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseDelHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminDelHomeCommand extends BaseDelHomeCommand {

    public AdminDelHomeCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(config.getAdminDelHomeUsageMessage());
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
    protected String getCannotDeleteDefaultHomeMessage(String[] args) {
        return config.getAdminCannotDeleteDefaultHomeMessage(args[1]);
    }

    @Override
    protected String getNoHomeMessage(String[] args) {
        return config.getAdminNoHomeMessage(args[1], args[2]);
    }

    @Override
    protected String getDelHomeSuccessMessage(String[] args) {
        return config.getAdminDelHomeSuccessMessage(args[1], args[2]);
    }
}
