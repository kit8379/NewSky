package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.command.BaseRemoveMemberCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.UUID;

public class IslandRemoveMemberCommand extends BaseRemoveMemberCommand {

    public IslandRemoveMemberCommand(CacheHandler cacheHandler) {
        super(cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /island removemember <player>");
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
        return ((Player) sender).getUniqueId();
    }
}