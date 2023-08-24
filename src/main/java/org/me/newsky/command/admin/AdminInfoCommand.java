package org.me.newsky.command.admin;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseInfoCommand;
import java.util.UUID;

public class AdminInfoCommand extends BaseInfoCommand {

    public AdminInfoCommand(CacheHandler cacheHandler) {
        super(cacheHandler);
    }

    @Override
    protected UUID getTargetUuid(CommandSender sender, String[] args) {
        return Bukkit.getOfflinePlayer(args[2]).getUniqueId();
    }
}
