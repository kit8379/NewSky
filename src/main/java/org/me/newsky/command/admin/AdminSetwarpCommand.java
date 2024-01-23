package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseSetwarpCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminSetwarpCommand extends BaseSetwarpCommand {

    public AdminSetwarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§eUsage: §b/islandadmin setwarp <player>");
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
        return "Player " + args[0] + " does not have an island.";
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return "You must be in the island of " + args[0] + " to set a warp point.";
    }

    @Override
    protected String getSetWarpSuccessMessage(String[] args) {
        return "Warp point set for " + args[0];
    }
}
