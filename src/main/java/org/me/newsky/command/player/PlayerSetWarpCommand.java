package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseSetWarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class PlayerSetWarpCommand extends BaseSetWarpCommand {

    public PlayerSetWarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(config.getPlayerSetWarpUsageMessage());
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected int getTargetWarpArgIndex() {
        return 1;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return config.getPlayerMustInIslandSetWarpMessage();
    }

    @Override
    protected String getSetWarpSuccessMessage(String[] args, String warpName) {
        return config.getPlayerSetWarpSuccessMessage(warpName);
    }
}
