package org.me.newsky.heartbeat;

import org.me.newsky.redis.RedisHandler;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartBeatSender {

    private final RedisHandler redisHandler;
    private final ScheduledExecutorService scheduler;
    private final String serverName;
    private final long heartbeatInterval;
    private final String heartbeatChannel;

    public HeartBeatSender(RedisHandler redisHandler, String serverName, long heartbeatInterval, String heartbeatChannel) {
        this.redisHandler = redisHandler;
        this.serverName = serverName;
        this.heartbeatInterval = heartbeatInterval;
        this.heartbeatChannel = heartbeatChannel;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        Runnable heartbeatTask = () -> {
            String message = serverName + " is alive at " + Instant.now().toString();
            redisHandler.publish(heartbeatChannel, message);
        };

        scheduler.scheduleAtFixedRate(heartbeatTask, 0, heartbeatInterval, TimeUnit.SECONDS);
    }

    public void stop() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
