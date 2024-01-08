package org.me.newsky.heartbeat;

import redis.clients.jedis.JedisPubSub;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartBeatListener extends JedisPubSub {

    private final ConcurrentHashMap<String, Instant> activeServers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final long timeoutSeconds;

    public HeartBeatListener(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        startCleanupTask();
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(this::removeInactiveServers, timeoutSeconds, timeoutSeconds, TimeUnit.SECONDS);
    }

    private void removeInactiveServers() {
        Instant now = Instant.now();
        activeServers.entrySet().removeIf(entry -> now.isAfter(entry.getValue().plusSeconds(timeoutSeconds)));
    }

    @Override
    public void onMessage(String channel, String message) {
        String[] parts = message.split(" is alive at ");
        if (parts.length == 2) {
            String serverId = parts[0];
            try {
                Instant timestamp = Instant.parse(parts[1]);
                activeServers.put(serverId, timestamp);
            } catch (Exception e) {
                // Handle parsing exceptions
            }
        }
    }

    public ConcurrentHashMap<String, Instant> getActiveServers() {
        return activeServers;
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
