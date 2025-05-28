package org.me.newsky.routing;

import org.me.newsky.cache.CacheHandler;

import java.util.Map;

public class RoundRobinServerSelector implements ServerSelector {

    private final CacheHandler cacheHandler;

    public RoundRobinServerSelector(CacheHandler cacheHandler) {
        this.cacheHandler = cacheHandler;
    }

    @Override
    public String selectServer(Map<String, String> activeServers) {
        if (activeServers.isEmpty()) return null;

        long index = cacheHandler.incrementAndGetRoundRobinCounter();

        if (index == -1) {
            return null;
        }

        int serverIndex = (int) (index % activeServers.size());
        return activeServers.keySet().toArray(new String[0])[serverIndex];
    }
}
