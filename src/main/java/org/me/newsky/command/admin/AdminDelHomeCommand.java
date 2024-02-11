package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseDelHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminDelHomeCommand extends BaseDelHomeCommand {

    public AdminDelHomeCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§eUsage: §b/islandadmin delhome <player> <homeName>");
            return false;
        }
        return true;
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
    }

    @Override
    protected int getTargetHomeArgIndex() {
        return 2;
    }

    @Override
    protected String getCannotDeleteDefaultHomeMessage(String[] args) {
        return "§cYou cannot delete " + args[1] + " the default home.";
    }

    @Override
    protected String getNoHomeMessage(String[] args) {
        return "§cPlayer " + args[1] + " does not have a home named " + args[2] + ".";
    }

    @Override
    protected String getDelHomeSuccessMessage(String[] args) {
        return "§aHome " + args[2] + " successfully deleted for player " + args[1] + ".";
    }
}
