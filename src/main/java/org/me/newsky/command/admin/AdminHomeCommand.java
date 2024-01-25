package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseHomeCommand;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

import java.util.UUID;

public class AdminHomeCommand extends BaseHomeCommand {

    public AdminHomeCommand(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        super(config, cacheHandler, islandHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage("§eUsage: §b/islandadmin home <player> [homeName]");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUUID(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected int getTargetHomeArgIndex() {
        return 2;
    }

    @Override
    protected String getNoIslandMessage(String[] args) {
        return "§cPlayer " + args[1] + " does not have an island.";
    }

    @Override
    protected String getNoHomesMessage(String[] args) {
        return "§cPlayer " + args[1] + " does not have any island homes.";
    }

    @Override
    protected String getNoHomeMessage(String[] args, String homeName) {
        return "§cPlayer " + args[1] + " does not have a home named " + homeName + ".";
    }

    @Override
    protected String getIslandHomeSuccessMessage(String homeName) {
        return "§aTeleported to " + homeName + "'s island home.";
    }
}
