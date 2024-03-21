package org.me.newsky.api;

import org.me.newsky.api.component.HomeAPI;
import org.me.newsky.api.component.IslandAPI;
import org.me.newsky.api.component.PlayerAPI;
import org.me.newsky.api.component.WarpAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.PreIslandHandler;

public class NewSkyAPI {
    public final IslandAPI islandAPI;
    public final PlayerAPI playerAPI;
    public final HomeAPI homeAPI;
    public final WarpAPI warpAPI;

    public NewSkyAPI(ConfigHandler config, CacheHandler cacheHandler, PreIslandHandler preIslandHandler) {
        this.islandAPI = new IslandAPI(config, cacheHandler, preIslandHandler);
        this.playerAPI = new PlayerAPI(cacheHandler);
        this.homeAPI = new HomeAPI(cacheHandler, preIslandHandler);
        this.warpAPI = new WarpAPI(cacheHandler, preIslandHandler);
    }
}
