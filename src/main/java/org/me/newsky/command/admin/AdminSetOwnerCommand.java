package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.base.BaseSetOwnerCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminSetOwnerCommand extends BaseSetOwnerCommand {

    public AdminSetOwnerCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
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
    protected boolean isOwner(CommandSender sender, UUID islandUuid) {
        return true;
    }

    @Override
    protected UUID getIslandOwnerUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[2]).getUniqueId();
    }

    @Override
    protected int getTargetOwnerArgIndex() {
        return 1;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getAdminNoIslandMessage(args[1]);
    }

    @Override
    protected String getAlreadyOwnerMessage(String[] args) {
        return config.getAdminAlreadyOwnerMessage(args[1], args[2]);
    }

    @Override
    protected String getSetOwnerSuccessMessage(String[] args) {
        return config.getAdminSetOwnerSuccessMessage(args[1], args[2]);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getAdminSetOwnerUsageMessage();
    }
}
