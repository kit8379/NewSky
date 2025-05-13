package org.me.newsky.routing;

import java.security.SecureRandom;
import java.util.Map;

public class RandomServerSelector implements ServerSelector {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String selectServer(Map<String, String> activeServers) {
        if (activeServers.isEmpty()) return null;
        String[] serverNames = activeServers.keySet().toArray(new String[0]);
        return serverNames[random.nextInt(serverNames.length)];
    }
}
