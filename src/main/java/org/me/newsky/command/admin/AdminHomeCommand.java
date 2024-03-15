package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.base.BaseHomeCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class AdminHomeCommand extends BaseHomeCommand {

    public AdminHomeCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
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
    protected String getIslandHomeSuccessMessage(String homeName) {
        return config.getAdminHomeSuccessMessage(homeName);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminHomeUsageMessage();
    }
}
