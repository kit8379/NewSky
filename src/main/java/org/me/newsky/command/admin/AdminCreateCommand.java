package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.base.BaseCreateCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class AdminCreateCommand extends BaseCreateCommand {

    public AdminCreateCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
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
    protected String getExistingIslandMessage(String[] args) {
        return config.getAdminAlreadyHasIslandMessage(args[1]);
    }

    @Override
    protected String getIslandCreateSuccessMessage(String[] args) {
        return config.getAdminCreateSuccessMessage(args[1]);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminCreateUsageMessage();
    }
}
