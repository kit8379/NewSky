package org.me.newsky.module;

import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;

import java.util.UUID;

public class Upgrade {
    private final ConfigHandler config;
    private final CacheHandler cacheHandler;

    public Upgrade(ConfigHandler config, CacheHandler cacheHandler) {
        this.config = config;
        this.cacheHandler = cacheHandler;
    }

    public void upgradeIslandRank(UUID islandUuid) {
        // Upgrade the island
    }
}
