package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseSetWarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandSetWarpCommand extends BaseSetWarpCommand {

    public IslandSetWarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eUsage: §b/island setwarp <warpName>");
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
        return "You do not have an island to set a warp point.";
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return "You must be in your island world to set a warp point.";
    }

    @Override
    protected String getSetWarpSuccessMessage(String[] args) {
        return "Warp point " + args[1] + " set successfully.";
    }
}
