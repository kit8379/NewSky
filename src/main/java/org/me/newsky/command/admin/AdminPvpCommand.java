package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BasePvpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminPvpCommand extends BasePvpCommand {

    public AdminPvpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §b/islandadmin pvp <player>");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return "Player does not have an island";
    }

    @Override
    protected String getIslandPvpEnableSuccessMessage(String[] args) {
        return "Island PvP enabled for " + args[1] + "'s island";
    }

    @Override
    protected String getIslandPvPDisableSuccessMessage(String[] args) {
        return "Island PvP disabled for " + args[1] + "'s island";
    }
}
