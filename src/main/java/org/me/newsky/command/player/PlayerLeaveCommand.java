package org.me.newsky.command.player;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.base.BaseLeaveCommand;
import org.me.newsky.config.ConfigHandler;

public class PlayerLeaveCommand extends BaseLeaveCommand {

    public PlayerLeaveCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }

    @Override
    public String getUsageCommandMessage() {
        return config.getPlayerLeaveUsageMessage();
    }
}
