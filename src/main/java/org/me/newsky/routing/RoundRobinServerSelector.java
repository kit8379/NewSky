package org.me.newsky.routing;

import org.me.newsky.cache.RedisCache;

import java.util.Map;

public class RoundRobinServerSelector implements ServerSelector {

    private final RedisCache redisCache;

    public RoundRobinServerSelector(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    @Override
    public String selectServer(Map<String, String> activeServers) {
        if (activeServers.isEmpty()) return null;

        long index = redisCache.getRoundRobinCounter();

        if (index == -1) {
            return null;
        }

        int serverIndex = (int) (index % activeServers.size());
        return activeServers.keySet().toArray(new String[0])[serverIndex];
    }
}
