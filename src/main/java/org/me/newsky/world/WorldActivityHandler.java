package org.me.newsky.world;

import org.me.newsky.NewSky;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WorldActivityHandler {

    private final Map<String, Integer> playerCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastEmptyTimestamps = new ConcurrentHashMap<>();

    private final NewSky plugin;

    public WorldActivityHandler(NewSky plugin) {
        this.plugin = plugin;
    }

    public void playerEnter(String worldName) {
        playerCounts.merge(worldName, 1, Integer::sum);
        lastEmptyTimestamps.remove(worldName);
        plugin.debug("WorldActivityHandler", "Player entered world: " + worldName + ", current count: " + playerCounts.get(worldName));
    }

    public void playerLeave(String worldName, long currentTime) {
        playerCounts.computeIfPresent(worldName, (name, count) -> {
            int newCount = count - 1;
            if (newCount <= 0) {
                lastEmptyTimestamps.put(name, currentTime);
                plugin.debug("WorldActivityHandler", "World " + name + " is now empty, timestamp recorded: " + currentTime);
                return null;
            }
            return newCount;
        });
    }

    public Map<String, Long> getInactiveWorlds(long thresholdMillis, long now) {
        Map<String, Long> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, Long> entry : lastEmptyTimestamps.entrySet()) {
            if (now - entry.getValue() >= thresholdMillis) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
