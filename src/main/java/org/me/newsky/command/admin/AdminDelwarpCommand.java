package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseDelwarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminDelwarpCommand extends BaseDelwarpCommand {

    public AdminDelwarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eUsage: §b/islandadmin delwarp <player>");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected String getNoWarpMessage(String[] args) {
        return "Player " + args[0] + " does not have an island or a warp point.";
    }

    @Override
    protected String getDelWarpSuccessMessage(String[] args) {
        return "Warp point successfully deleted from " + args[0] + "'s island.";
    }
}
