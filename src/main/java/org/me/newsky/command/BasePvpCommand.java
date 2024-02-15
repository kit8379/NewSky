package org.me.newsky.command;

import org.bukkit.command.CommandSender;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.Optional;
import java.util.UUID;

public abstract class BasePvpCommand {

    protected final ConfigHandler config;
    protected final CacheHandler cacheHandler;

    public BasePvpCommand(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (!validateArgs(sender, args)) {
            return true;
        }

        UUID targetUuid = getTargetUuid(sender, args);

        Optional<UUID> islandUuidOpt = cacheHandler.getIslandUuidByPlayerUuid(targetUuid);
        if (islandUuidOpt.isEmpty()) {
            sender.sendMessage(getNoIslandMessage(args));
            return true;
        }
        UUID islandUuid = islandUuidOpt.get();

        if (cacheHandler.getIslandPvp(islandUuid)) {
            cacheHandler.updateIslandPvp(islandUuid, false);
            sender.sendMessage(getIslandPvPDisableSuccessMessage(args));
        } else {
            cacheHandler.updateIslandPvp(islandUuid, true);
            sender.sendMessage(getIslandPvpEnableSuccessMessage(args));
        }

        return true;
    }

    protected abstract boolean validateArgs(CommandSender sender, String[] args);

    protected abstract UUID getTargetUuid(CommandSender sender, String[] args);

    protected abstract String getNoIslandMessage(String[] args);

    protected abstract String getIslandPvpEnableSuccessMessage(String[] args);

    protected abstract String getIslandPvPDisableSuccessMessage(String[] args);
}
