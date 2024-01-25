package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseSetHomeCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class AdminSetHomeCommand extends BaseSetHomeCommand {

    public AdminSetHomeCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected boolean validateArgs(CommandSender sender, String[] args) {
        return args.length == 3;
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
    protected String getNoIslandMessage(String[] args) {
        return "§cPlayer " + args[1] + " does not have an island.";
    }

    @Override
    protected String getMustInIslandMessage(String[] args) {
        return "§cYou must be in the island world of " + args[1] + " to set their home.";
    }

    @Override
    protected String getSetHomeSuccessMessage(String[] args, String homeName) {
        return "§aHome " + homeName + " set successfully for " + args[1] + ".";
    }
}
