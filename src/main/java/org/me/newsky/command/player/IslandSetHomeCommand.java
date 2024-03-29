package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseSetHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandSetHomeCommand extends BaseSetHomeCommand {

    public IslandSetHomeCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if(args.length < 1 || args.length > 2) {
            sender.sendMessage(config.getPlayerSetHomeUsageMessage());
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected int getTargetHomeArgIndex() {
        return 1;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return config.getPlayerNoIslandMessage();
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return config.getPlayerMustInIslandSetHomeMessage();
    }

    @Override
    protected String getSetHomeSuccessMessage(String[] args, String homeName) {
        return config.getPlayerSetHomeSuccessMessage(homeName);
    }
}
