package org.me.newsky.api;

import org.me.newsky.api.component.HomeAPI;
import org.me.newsky.api.component.IslandAPI;
import org.me.newsky.api.component.WarpAPI;
import org.me.newsky.cache.CacheHandler;
import org.me.newsky.island.IslandHandler;

public class NewSkyAPI {
    public final IslandAPI islandAPI;
    public final HomeAPI homeAPI;
    public final WarpAPI warpAPI;

    public NewSkyAPI(CacheHandler cacheHandler, IslandHandler islandHandler) {
        this.islandAPI = new IslandAPI(cacheHandler, islandHandler);
        this.homeAPI = new HomeAPI(cacheHandler);
        this.warpAPI = new WarpAPI(cacheHandler);
    }

}
