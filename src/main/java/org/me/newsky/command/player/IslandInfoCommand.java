package org.me.newsky.command.player;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseInfoCommand;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class IslandInfoCommand extends BaseInfoCommand {

    public IslandInfoCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        if (args.length > 1) {
            return Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        } else {
            return ((Player) sender).getUniqueId();
        }
    }
}
