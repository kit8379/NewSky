package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseInfoCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminInfoCommand extends BaseInfoCommand {

    public AdminInfoCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[2]).getUniqueId();
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminInfoUsageMessage();
    }
}
