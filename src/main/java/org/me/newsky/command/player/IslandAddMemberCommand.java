package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.command.BaseAddMemberCommand;
import org.me.newsky.cache.CacheHandler;

import java.util.UUID;

public class IslandAddMemberCommand extends BaseAddMemberCommand {

    public IslandAddMemberCommand(CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /island addmember <player>");
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
        return ((Player) sender).getUniqueId();
    }
}
