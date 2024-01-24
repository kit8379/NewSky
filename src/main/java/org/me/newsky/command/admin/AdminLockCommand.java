package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseLockCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminLockCommand extends BaseLockCommand {

    public AdminLockCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §b/islandadmin create <player>");
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
        return "§cPlayer " + args[1] + " does not have an island";
    }

    @Override
    protected String getIslandUnLockSuccessMessage(String[] args) {
        return "§aIsland unlocked for " + args[1] + ".";
    }

    @Override
    protected String getIslandLockSuccessMessage(String[] args) {
        return "§aIsland locked for " + args[1] + ".";
    }
}
