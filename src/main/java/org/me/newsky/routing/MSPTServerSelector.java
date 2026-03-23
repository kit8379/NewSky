package org.me.newsky.routing;

import org.me.newsky.state.ServerSelectorState;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MSPTServerSelector implements ServerSelector {

    private final ServerSelectorState serverSelectorState;
    private final SecureRandom random = new SecureRandom();

    public MSPTServerSelector(ServerSelectorState serverSelectorState) {
        this.serverSelectorState = serverSelectorState;
    }

    @Override
    public String selectServer(Map<String, String> activeServers) {
        if (activeServers.isEmpty()) return null;

        List<String> bestServers = new ArrayList<>();
        double minMspt = Double.MAX_VALUE;

        for (String server : activeServers.keySet()) {
            double mspt = serverSelectorState.getServerMSPT(server);
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
