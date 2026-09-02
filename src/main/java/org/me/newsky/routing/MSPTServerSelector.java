package org.me.newsky.routing;

import org.me.newsky.cluster.ServerRegistry;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MSPTServerSelector implements ServerSelector {

    private final ServerRegistry serverRegistry;
    private final SecureRandom random = new SecureRandom();

    public MSPTServerSelector(ServerRegistry serverRegistry) {
        this.serverRegistry = serverRegistry;
    }

    @Override
    public String selectServer(Map<String, String> activeServers) {
        if (activeServers.isEmpty()) return null;

        List<String> bestServers = new ArrayList<>();
        double minMspt = Double.MAX_VALUE;

        for (String server : activeServers.keySet()) {
            double mspt = serverRegistry.getServerMSPT(server);
            if (mspt == -1) continue;

            if (mspt < minMspt) {
                minMspt = mspt;
                bestServers.clear();
                bestServers.add(server);
            } else if (mspt == minMspt) {
                bestServers.add(server);
            }
        }

        return bestServers.isEmpty() ? null : bestServers.get(random.nextInt(bestServers.size()));
    }
}
