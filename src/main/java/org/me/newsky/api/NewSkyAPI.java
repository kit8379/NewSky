package org.me.newsky.api;

import org.me.newsky.api.component.HomeAPI;
import org.me.newsky.api.component.IslandAPI;
import org.me.newsky.api.component.PlayerAPI;
import org.me.newsky.api.component.WarpAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.config.ConfigHandler;
import org.me.newsky.island.IslandHandler;

public class NewSkyAPI {
    public final IslandAPI islandAPI;
    public final PlayerAPI playerAPI;
    public final HomeAPI homeAPI;
    public final WarpAPI warpAPI;

    public NewSkyAPI(ConfigHandler config, CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.islandAPI = new IslandAPI(config, cacheHandler, islandHandler);
        this.playerAPI = new PlayerAPI(cacheHandler);
        this.homeAPI = new HomeAPI(cacheHandler, islandHandler);
        this.warpAPI = new WarpAPI(cacheHandler, islandHandler);
    }
}
