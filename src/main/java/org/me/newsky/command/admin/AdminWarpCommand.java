package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseWarpCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class AdminWarpCommand extends BaseWarpCommand {

    public AdminWarpCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(config.getAdminWarpUsageMessage());
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected int getTargetWarpArgIndex() {
        return 2;
    }
}
