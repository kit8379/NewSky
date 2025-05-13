package org.me.newsky.routing;

import java.util.Map;

public interface ServerSelector {
    String selectServer(Map<String, String> activeServers);
}
