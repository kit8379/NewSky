package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseDelHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandDelHomeCommand extends BaseDelHomeCommand {

    public IslandDelHomeCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(config.getPlayerDelHomeUsageMessage());
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
    protected String getCannotDeleteDefaultHomeMessage(String args[]) {
        return config.getPlayerCannotDeleteDefaultHomeMessage();
    }

    @Override
    protected String getNoHomeMessage(String[] args) {
        return config.getPlayerNoHomeMessage(args[1]);
    }

    @Override
    protected String getDelHomeSuccessMessage(String[] args) {
        return config.getPlayerDelHomeSuccessMessage(args[1]);
    }
}
