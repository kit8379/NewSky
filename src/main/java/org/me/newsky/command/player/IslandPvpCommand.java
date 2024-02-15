package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BasePvpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandPvpCommand extends BasePvpCommand {

    public IslandPvpCommand(ConfigHandler config, CacheHandler cacheHandler) {
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
    protected String getIslandPvpEnableSuccessMessage(String[] args) {
        return "Island PvP enabled for your island";
    }

    @Override
    protected String getIslandPvPDisableSuccessMessage(String[] args) {
        return "Island PvP disabled for your island";
    }
}
