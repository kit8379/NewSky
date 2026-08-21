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

    /**
     * Marks a freshly loaded world as empty-since-now unless someone is already inside. Without
     * this seed, a world loaded for a teleport that never arrives has no empty timestamp at all
     * and would stay loaded (holding its claim) until the server restarts.
     */
    public void worldLoaded(String worldName, long currentTime) {
        if (!playerCounts.containsKey(worldName)) {
            lastEmptyTimestamps.putIfAbsent(worldName, currentTime);
            plugin.debug("WorldActivityHandler", "World " + worldName + " loaded empty, timestamp seeded: " + currentTime);
        }
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

    /**
     * Re-checked at the moment of unload, after the sweep nominated this world: a player may have
     * entered in between, which removes the empty-timestamp and must veto the unload.
     */
    public boolean isStillInactive(String worldName, long thresholdMillis, long now) {
        Long emptySince = lastEmptyTimestamps.get(worldName);
        return emptySince != null && now - emptySince >= thresholdMillis && !playerCounts.containsKey(worldName);
    }

    public void clearWorld(String worldName) {
        playerCounts.remove(worldName);
        lastEmptyTimestamps.remove(worldName);
        plugin.debug("WorldActivityHandler", "Cleared activity tracking for world: " + worldName);
    }
}