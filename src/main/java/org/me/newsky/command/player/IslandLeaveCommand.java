package org.me.newsky.command.player;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.command.BaseLeaveCommand;
import org.me.newsky.config.ConfigHandler;

public class IslandLeaveCommand extends BaseLeaveCommand {

    public IslandLeaveCommand(ConfigHandler config, CacheHandler cacheHandler) {
        super(config, cacheHandler);
    }
}
