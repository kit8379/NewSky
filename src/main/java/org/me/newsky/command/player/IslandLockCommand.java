package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseLockCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandLockCommand extends BaseLockCommand {

    public IslandLockCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return "You do not have an island";
    }

    @Override
    protected String getIslandUnLockSuccessMessage(String[] args) {
        return "Island unlocked";
    }

    @Override
    protected String getIslandLockSuccessMessage(String[] args) {
        return "Island locked";
    }
}
