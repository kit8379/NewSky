package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.command.BaseAddMemberCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.UUID;

public class AdminAddMemberCommand extends BaseAddMemberCommand {

    public AdminAddMemberCommand(CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /islandadmin addmember <player> <islandowner>");
            return false;
        }
        return true;
    }

    @Override
    protected int getTargetAddArgIndex() {
        return 1;
    }

    @Override
    protected UUID getIslandOwnerUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[2]).getUniqueId();
    }
}
