package org.me.newsky.heartbeat;

import org.me.newsky.config.ConfigHandler;
import org.me.newsky.redis.RedisHandler;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class HeartBeatHandler {

    private final ConfigHandler config;
    private final HeartBeatSender heartbeatSender;
    private final HeartBeatListener heartbeatListener;

    public HeartBeatHandler(ConfigHandler config, RedisHandler redisHandler) {
        this.config = config;

        // HeartBeat config
        String serverName = config.getServerName();
        long heartbeatInterval = 10;
        long timeoutSeconds = 5;
        String heartbeatChannel = "newsky-heartbeat";

        // Create HeartBeatSender and HeartBeatListener
        this.heartbeatSender = new HeartBeatSender(redisHandler, serverName, heartbeatInterval, heartbeatChannel);
        this.heartbeatListener = new HeartBeatListener(timeoutSeconds);
        redisHandler.subscribe(heartbeatListener, heartbeatChannel);
    }

    public void start() {
        heartbeatSender.start();
    }

    public void stop() {
        heartbeatSender.stop();
        heartbeatListener.shutdown();
    }

    public ConcurrentHashMap<String, Instant> getActiveServers() {
        return heartbeatListener.getActiveServers();
    }
}
