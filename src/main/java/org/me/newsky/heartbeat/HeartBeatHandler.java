package org.me.newsky.heartbeat;

import org.me.newsky.NewSky;
import org.me.newsky.config.ConfigHandler;

public class HeartBeatHandler {

    private final NewSky plugin;
    private final ConfigHandler config;
    private final String serverID;
    private final int heartbeatInterval;

    public HeartBeatHandler(NewSky plugin, ConfigHandler config, String serverID) {
        this.plugin = plugin;
        this.config = config;
        this.serverID = serverID;
        this.heartbeatInterval = config.getHeartbeatInterval();
    }

    public void start() {
        // Start the heartbeat
    }

    public void stop() {
        // Stop the heartbeat
    }

    // TODO: Implement the heartbeat logic
}
