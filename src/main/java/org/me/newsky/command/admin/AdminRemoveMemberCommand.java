package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.command.BaseRemoveMemberCommand;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminRemoveMemberCommand extends BaseRemoveMemberCommand {

    public AdminRemoveMemberCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: §b/island admin removemember <player> <island owner>");
            return false;
        }
        return true;
    }

    @Override
    protected int getTargetRemoveArgIndex() {
        return 1;
    }

    @Override
    protected UUID getIslandOwnerUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[2]).getUniqueId();
    }
}