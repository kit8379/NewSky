package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseRemoveMemberCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandRemoveMemberCommand extends BaseRemoveMemberCommand {

    public IslandRemoveMemberCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(config.getPlayerRemoveMemberUsageMessage());
            return false;
        }
        return true;
    }

    @Override
    protected boolean isNotSelf(CommandSender sender, String[] args) {
        if (args[1].equals(sender.getName())) {
            sender.sendMessage(config.getPlayerRemoveMemberCannotRemoveSelfMessage());
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

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getIslandRemoveMemberSuccessMessage(String[] args) {
        return config.getPlayerRemoveMemberSuccessMessage(args[1]);
    }
}