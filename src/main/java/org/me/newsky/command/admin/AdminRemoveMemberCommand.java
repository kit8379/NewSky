package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.command.BaseRemoveMemberCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.UUID;

public class AdminRemoveMemberCommand extends BaseRemoveMemberCommand {

    public AdminRemoveMemberCommand(CacheHandler cacheHandler) {
        super(cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /islandadmin removemember <player> <islandowner>");
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