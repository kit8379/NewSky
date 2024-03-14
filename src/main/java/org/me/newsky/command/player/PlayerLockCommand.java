package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseLockCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerLockCommand extends BaseLockCommand {

    public PlayerLockCommand(ConfigHandler config, CacheHandler cacheHandler) {
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
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getIslandUnLockSuccessMessage(String[] args) {
        return config.getPlayerUnLockSuccessMessage();
    }

    @Override
    protected String getIslandLockSuccessMessage(String[] args) {
        return config.getPlayerLockSuccessMessage();
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerLockUsageMessage();
    }
}
