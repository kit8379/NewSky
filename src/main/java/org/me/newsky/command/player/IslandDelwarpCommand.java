package org.me.newsky.command.player;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseDelwarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandDelwarpCommand extends BaseDelwarpCommand {

    public IslandDelwarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("§eUsage: §b/island delwarp");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return ((Player) sender).getUniqueId();
    }

    @Override
    protected String getNoWarpMessage(String[] args) {
        return "You do not have an island to delete a warp point.";
    }

    @Override
    protected String getDelWarpSuccessMessage(String[] args) {
        return "Warp point successfully deleted from your island.";
    }
}
