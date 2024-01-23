package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BaseDelwarpCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BaseDelwarpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        UUID targetUuid = getTargetUuid(sender, args);

        Optional<String> warpLocationOpt = cacheHandler.getWarpLocation(targetUuid);
        if (warpLocationOpt.isEmpty()) {
            sender.sendMessage(getNoWarpMessage(args));
            return true;
        }

        cacheHandler.deleteWarpPoint(targetUuid);

        sender.sendMessage(getDelWarpSuccessMessage(args));

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);
    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);
    protected abstract String getNoWarpMessage(String[] args);
    protected abstract String getDelWarpSuccessMessage(String[] args);
}
