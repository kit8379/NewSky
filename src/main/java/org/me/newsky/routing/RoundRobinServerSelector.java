package org.me.newsky.routing;

import org.me.newsky.cache.RuntimeCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoundRobinServerSelector implements ServerSelector {

    private final RuntimeCache runtimeCache;

    public RoundRobinServerSelector(RuntimeCache runtimeCache) {
        this.runtimeCache = runtimeCache;
    }

    @Override
    public String selectServer(Map<String, String> activeServers) {
        if (activeServers.isEmpty()) return null;

        long index = runtimeCache.getRoundRobinCounter();

        if (index == -1) {
            return null;
        }

        List<String> servers = new ArrayList<>(activeServers.keySet());
        servers.sort(String::compareTo);

        int serverIndex = (int) (index % servers.size());
        return servers.get(serverIndex);
    }
}
