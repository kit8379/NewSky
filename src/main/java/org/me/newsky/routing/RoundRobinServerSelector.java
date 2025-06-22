package org.me.newsky.routing;

import org.me.newsky.redis.RedisCache;

import java.util.ArrayList;
import java.util.List;
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

        List<String> servers = new ArrayList<>(activeServers.keySet());
        servers.sort(String::compareTo);

        int serverIndex = (int) (index % servers.size());
        return servers.get(serverIndex);
    }
}
